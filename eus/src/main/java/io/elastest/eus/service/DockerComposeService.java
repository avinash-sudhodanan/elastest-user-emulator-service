/*
 * (C) Copyright 2017-2019 ElasTest (http://elastest.io/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package io.elastest.eus.service;

import static io.elastest.eus.docker.DockerContainer.dockerBuilder;
import static java.util.Arrays.asList;

import java.io.IOException;
import java.util.List;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import com.github.dockerjava.api.model.Ports.Binding;
import com.github.dockerjava.api.model.Volume;

import io.elastest.eus.docker.DockerException;
import io.elastest.eus.external.DockerComposeUiApi;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava.RxJavaCallAdapterFactory;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Service implementation for Docker Compose.
 *
 * @author Boni Garcia (boni.garcia@urjc.es)
 * @since 0.0.1
 */
@Service
public class DockerComposeService {

    private final Logger log = LoggerFactory
            .getLogger(DockerComposeService.class);

    @Value("${docker.compose.ui.exposedport}")
    private int dockerComposeUiPort;

    @Value("${docker.compose.ui.image}")
    private String dockerComposeUiImageId;

    @Value("${docker.compose.ui.prefix}")
    private String dockerComposeUiPrefix;

    private String dockerComposeUiUrl;
    private String dockerComposeUiContainerName;
    private DockerComposeUiApi dockerComposeUi;

    private DockerService dockerService;

    public DockerComposeService(DockerService dockerService) {
        this.dockerService = dockerService;
    }

    @PostConstruct
    public void setup() throws IOException, InterruptedException {
        dockerComposeUiContainerName = dockerService
                .generateContainerName(dockerComposeUiPrefix);

        int dockerComposeBindPort = dockerService.findRandomOpenPort();
        Binding bindNoVncPort = Ports.Binding.bindPort(dockerComposeBindPort);
        ExposedPort exposedNoVncPort = ExposedPort.tcp(dockerComposeUiPort);

        dockerComposeUiUrl = "http://" + dockerService.getDockerServerIp() + ":"
                + dockerComposeBindPort;

        log.debug("Starting docker-compose-ui container: {}",
                dockerComposeUiContainerName);

        List<PortBinding> portBindings = asList(
                new PortBinding(bindNoVncPort, exposedNoVncPort));
        String path = "/var/run/docker.sock";
        Volume volume = new Volume(path);
        List<Volume> volumes = asList(volume);
        List<Bind> binds = asList(new Bind(path, volume));

        dockerService
                .startAndWaitContainer(dockerBuilder(dockerComposeUiImageId,
                        dockerComposeUiContainerName).portBindings(portBindings)
                                .volumes(volumes).binds(binds).build());

        Retrofit retrofit = new Retrofit.Builder()
                .addCallAdapterFactory(RxJavaCallAdapterFactory.create())
                .addConverterFactory(GsonConverterFactory.create())
                .baseUrl(dockerComposeUiUrl).build();
        dockerComposeUi = retrofit.create(DockerComposeUiApi.class);

        log.debug("docker-compose-ui up and running on URL: {}",
                dockerComposeUiUrl);
    }

    @PreDestroy
    public void teardown() {
        log.debug("Stopping docker-compose-ui container: {}",
                dockerComposeUiContainerName);
        dockerService.stopAndRemoveContainer(dockerComposeUiContainerName);
    }

    public boolean createProject(String projectName, String dockerComposeYml)
            throws IOException {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("name", projectName);
        jsonObject.put("yml", dockerComposeYml.replaceAll("'", "\""));
        RequestBody data = RequestBody.create(
                MediaType.parse("application/json"), jsonObject.toString());

        log.debug("Creating Docker Compose with data: {}",
                jsonObject.toString());
        Response<ResponseBody> response = dockerComposeUi.createProject(data)
                .execute();
        log.trace("Response: {} -- {}", response.code(),
                response.body().string());

        if (!response.isSuccessful()) {
            throw new DockerException(response.errorBody().string());
        }
        return true;
    }

    public boolean startProject(String projectName) throws IOException {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("id", projectName);
        RequestBody data = RequestBody.create(
                MediaType.parse("application/json"), jsonObject.toString());

        log.debug("Starting Docker Compose project with data: {}",
                jsonObject.toString());
        Response<ResponseBody> response = dockerComposeUi.dockerComposeUp(data)
                .execute();

        if (!response.isSuccessful()) {
            throw new DockerException(response.errorBody().string());
        }
        return true;
    }

    public boolean stopProject(String projectName) throws IOException {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("id", projectName);
        RequestBody data = RequestBody.create(
                MediaType.parse("application/json"), jsonObject.toString());

        log.debug("Stopping Docker Compose project with data: {}",
                jsonObject.toString());
        Response<ResponseBody> response = dockerComposeUi
                .dockerComposeDown(data).execute();

        if (!response.isSuccessful()) {
            throw new DockerException(response.errorBody().string());
        }
        return true;
    }

}

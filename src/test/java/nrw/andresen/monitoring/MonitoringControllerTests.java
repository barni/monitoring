/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nrw.andresen.monitoring;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@TestPropertySource(locations = "classpath:application-test.properties")
public class MonitoringControllerTests {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    public void setup() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    @Test
    @WithMockUser
    public void paramGreetingShouldReturnTailoredMessage() throws Exception {

        this.mockMvc.perform(get("/heartBeat").param("name", "SpringCommunity"))
                .andDo(print()).andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("SpringCommunity"));
    }

    /**
     * Ein Name mit HTML-Sonderzeichen darf gar nicht erst gespeichert werden,
     * sonst landet er als Stored XSS auf der Statusseite.
     */
    @Test
    @WithMockUser
    public void heartBeatShouldRejectNameWithHtmlCharacters() throws Exception {
        this.mockMvc.perform(get("/heartBeat").param("name", "<script>alert(1)</script>"))
                .andExpect(status().isBadRequest());
    }

    /**
     * Zeilenumbrueche im Namen wuerden gefaelschte Logzeilen ermoeglichen.
     */
    @Test
    @WithMockUser
    public void heartBeatShouldRejectNameWithNewline() throws Exception {
        this.mockMvc.perform(get("/heartBeat").param("name", "ok\nINFO gefaelschte Zeile"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    public void heartBeatShouldRejectOverlongName() throws Exception {
        this.mockMvc.perform(get("/heartBeat").param("name", "A".repeat(65)))
                .andExpect(status().isBadRequest());
    }

    /**
     * Der zustandsaendernde Heartbeat ist ausschliesslich ueber GET erreichbar;
     * andere Methoden werden nicht mehr stillschweigend akzeptiert.
     */
    @Test
    @WithMockUser
    public void heartBeatShouldRejectPost() throws Exception {
        this.mockMvc.perform(post("/heartBeat").param("name", "SERVICE1"))
                .andExpect(status().isMethodNotAllowed());
    }

    /**
     * Die Statusseite liefert ausdruecklich text/html und darf keine
     * unescapten Namen enthalten.
     */
    @Test
    @WithMockUser
    public void statusShouldEscapeServiceNames() throws Exception {
        this.mockMvc.perform(get("/heartBeat").param("name", "SERVICE-1"))
                .andExpect(status().isOk());

        this.mockMvc.perform(get("/status").header(HttpHeaders.ACCEPT, "text/html"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/html"))
                .andExpect(content().string(Matchers.containsString("Name: SERVICE-1")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("<script"))));
    }
}

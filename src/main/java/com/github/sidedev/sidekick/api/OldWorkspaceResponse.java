package com.github.sidedev.sidekick.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class OldWorkspaceResponse {
    @JsonProperty("workspaces")
    private List<Workspace> workspaces;

    public List<Workspace> getWorkspaces() {
        return workspaces;
    }

    public static class Workspace {
        private String id;
        private String name;
        @JsonProperty("localRepoDir")
        private String localRepoDir;
        private String created;
        private String updated;

        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public String getLocalRepoDir() {
            return localRepoDir;
        }

        public String getCreated() {
            return created;
        }

        public String getUpdated() {
            return updated;
        }
    }
}
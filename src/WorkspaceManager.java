import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.util.HashMap;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.ArrayList;
import java.io.IOException;
import java.sql.SQLException;

public class WorkspaceManager {
    private HashMap<String, Workspace> workspaces = new HashMap<>();
    public boolean createWorkspace(String name) {
        if(name.equals("") || workspaces.get(name) != null){
            return false;
        }
        Workspace workspace = new Workspace(name);
        workspaces.put(name,workspace);
        return true;
    }
    public Workspace findWorkspaceByName(String name) {
        return workspaces.get(name);
    }
    public void saveWorkspaceNames(Path file) throws IOException {
        ArrayList<String> lines = new ArrayList<>();

        for (String name : workspaces.keySet()) {
            lines.add(name);
        }

        Files.write(file, lines, StandardCharsets.UTF_8);
    }
    public void loadWorkspaceNames(Path file) throws IOException {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);

        for (String name : lines) {
            createWorkspace(name);
        }
    }
    public void saveAll(Path file) throws IOException {
        ArrayList<String> lines = new ArrayList<>();

        for (Workspace workspace : workspaces.values()) {
            lines.add(workspace.getName());
            lines.add(String.valueOf(workspace.getAssetCount()));
            lines.addAll(workspace.exportAssetLines());
        }

        Files.write(file, lines, StandardCharsets.UTF_8);
    }
    public void loadAll(Path file) throws IOException {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);

        HashMap<String, Workspace> loadedWorkspaces = new HashMap<>();
        int index = 0;

        while (index < lines.size()) {
            if (lines.size() - index < 2) {
                throw new IOException("工作区信息不完整");
            }

            String workspaceName = lines.get(index);
            int assetCount;
            try {
                assetCount = Integer.parseInt(lines.get(index + 1));
            } catch (NumberFormatException exception) {
                throw new IOException("资源数量必须是有效整数", exception);
            }
            index += 2;

            if (workspaceName.isBlank() || loadedWorkspaces.containsKey(workspaceName)) {
                throw new IOException("工作区名称为空或重复");
            }

            if (assetCount < 0 || assetCount > (lines.size() - index) / 2) {
                throw new IOException("资源数量无效，或资源数据不完整");
            }

            Workspace workspace = new Workspace(workspaceName);
            for (int i = 0; i < assetCount; i++) {
                String name = lines.get(index);
                String path = lines.get(index + 1);
                workspace.addAsset(name,path);
                index += 2;
            }

            loadedWorkspaces.put(workspaceName, workspace);
        }

        this.workspaces = loadedWorkspaces;
    }
    public void loadFromDatabase(WorkspaceDatabase database) throws SQLException {
        HashMap<String, Workspace> loadedWorkspaces = new HashMap<>();

        for (String name : database.findWorkspaceNames()) {
            Workspace workspace = new Workspace(name);
            int workspaceId = database.findWorkspaceId(name);

            ArrayList<Asset> assets = database.findWorkspaceAssets(workspaceId);
            for (Asset asset : assets) {
                workspace.addAsset(asset);
            }

            loadedWorkspaces.put(name, workspace);
        }

        this.workspaces = loadedWorkspaces;
    }
    public void saveToDatabase(WorkspaceDatabase database) throws SQLException {
        try (Connection connection = database.openConnection()) {
            connection.setAutoCommit(false);

            try {
                HashMap<String, String> savedNames = new HashMap<>();

                for (Workspace workspace : workspaces.values()) {
                    database.addWorkspace(connection, workspace.getName());
                    int workspaceId = database.findWorkspaceId(connection, workspace.getName());

                    database.clearWorkspaceLinks(connection, workspaceId);

                    for (Asset asset : workspace.getAssets()) {
                        String previousName = savedNames.putIfAbsent(asset.getPath(), asset.getName());

                        if (previousName != null && !previousName.equals(asset.getName())) {
                            throw new SQLException("同一路径的资源名称不一致：" + asset.getPath());
                        }

                        database.saveAsset(connection, asset.getName(), asset.getPath());
                        int assetId = database.findAssetId(connection, asset.getPath());
                        database.addAssetLink(connection, workspaceId, assetId);
                    }
                }
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }
}
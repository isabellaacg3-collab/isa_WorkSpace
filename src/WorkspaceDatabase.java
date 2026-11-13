import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import org.sqlite.SQLiteConfig;
import java.sql.Statement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;

public class WorkspaceDatabase {
    private final Path file;
    public WorkspaceDatabase(Path file) {
        this.file = file.toAbsolutePath().normalize();
    }
    public Connection openConnection() throws SQLException {
        SQLiteConfig config = new SQLiteConfig();
        config.enforceForeignKeys(true);

        String url = "jdbc:sqlite:" + this.file;
        return DriverManager.getConnection(url, config.toProperties());
    }
    public int findWorkspaceId(String name) throws SQLException {
        try (Connection connection = openConnection()) {
            return findWorkspaceId(connection, name);
        }
    }
    public int findWorkspaceId(Connection connection,String name) throws SQLException {
        String sql = "SELECT id FROM workspaces WHERE name = ?";

        try (PreparedStatement query = connection.prepareStatement(sql)) {
            query.setString(1, name);

            try (ResultSet result = query.executeQuery()) {
                if (result.next()) {
                    return result.getInt("id");
                }
            }
        }

        throw new SQLException("未找到工作区：" + name);
    }
    public int findAssetId(String path) throws SQLException {
        try (Connection connection = openConnection()) {
            return findAssetId(connection, path);
        }
    }
    public int findAssetId(Connection connection,String path) throws SQLException{
        String sql = " SELECT id FROM assets WHERE path = ?";

        try (PreparedStatement query = connection.prepareStatement(sql)){
            query.setString(1, path);

            try (ResultSet result = query.executeQuery()) {
                if (result.next()) {
                    return result.getInt("id");
                }
            }
        }

        throw new SQLException("未找到资源：" + path);
    }
    public void initialize() throws SQLException {
        String assetSql = """
            CREATE TABLE IF NOT EXISTS assets (
                id INTEGER PRIMARY KEY,
                name TEXT NOT NULL,
                path TEXT NOT NULL UNIQUE
            );
            """;
        String createWorkspacesSql = """
            CREATE TABLE IF NOT EXISTS workspaces (
                id INTEGER PRIMARY KEY,
                name TEXT NOT NULL UNIQUE
            );
            """;
        String createLinksSql = """
            CREATE TABLE IF NOT EXISTS workspace_assets (
                workspace_id INTEGER NOT NULL,
                asset_id INTEGER NOT NULL,
                PRIMARY KEY (workspace_id, asset_id),
                FOREIGN KEY (workspace_id) REFERENCES workspaces(id),
                FOREIGN KEY (asset_id) REFERENCES assets(id)
            );
            """;

        try (Connection connection = openConnection();
             Statement statement = connection.createStatement()) {

            statement.executeUpdate(assetSql);
            statement.executeUpdate(createWorkspacesSql);
            statement.executeUpdate(createLinksSql);
        }
    }
    public int addWorkspace(String name) throws SQLException {
        try (Connection connection = openConnection()) {
            return addWorkspace(connection, name);
        }
    }
    public int addWorkspace(Connection connection, String name) throws SQLException {
        String sql = """
            INSERT INTO workspaces (name)
            VALUES (?)
            ON CONFLICT(name) DO NOTHING;
            """;

        try (PreparedStatement insert = connection.prepareStatement(sql)) {
            insert.setString(1, name);
            return insert.executeUpdate();
        }
    }
    public int addAsset(String name, String path) throws SQLException {
        try (Connection connection = openConnection()) {
            return addAsset(connection, name, path);
        }
    }
    public int addAsset(Connection connection, String name, String path) throws SQLException {
        String insertSql = """
                INSERT INTO assets (name, path)
                VALUES (?, ?)
                ON CONFLICT(path) DO NOTHING;
                """;
        try (PreparedStatement insert = connection.prepareStatement(insertSql)) {
            insert.setString(1, name);
            insert.setString(2, path);

            return insert.executeUpdate();
        }
    }
    public int addAssetLink(int workspaceId, int assetId) throws SQLException {
        try (Connection connection = openConnection()) {
            return addAssetLink(connection, workspaceId, assetId);
        }
    }
    public int addAssetLink(Connection connection,int workspaceId, int assetId) throws SQLException {
        String sql = """
            INSERT INTO workspace_assets (workspace_id, asset_id)
            VALUES (?, ?)
            ON CONFLICT(workspace_id, asset_id) DO NOTHING;
            """;

        try (PreparedStatement link = connection.prepareStatement(sql)) {
            link.setInt(1, workspaceId);
            link.setInt(2, assetId);
            return link.executeUpdate();
        }
    }
    public int removeAssetLink(int workspaceId, int assetId) throws SQLException {
        String sql = """
            DELETE FROM workspace_assets
            WHERE workspace_id = ? AND asset_id = ?;
            """;

        try (Connection connection = openConnection();
             PreparedStatement unlink = connection.prepareStatement(sql)) {
            unlink.setInt(1,workspaceId);
            unlink.setInt(2,assetId);

            return unlink.executeUpdate();
        }
    }
    public ArrayList<Asset> findWorkspaceAssets(int workspaceId) throws SQLException {
        ArrayList<Asset> assets = new ArrayList<>();

        String sql = """
            SELECT assets.name, assets.path FROM assets
            JOIN workspace_assets ON assets.id = workspace_assets.asset_id
            WHERE workspace_assets.workspace_id = ?
            ORDER BY assets.id;
            """;
        try (Connection connection = openConnection();
             PreparedStatement query = connection.prepareStatement(sql)) {
            query.setInt(1, workspaceId);

            try (ResultSet result = query.executeQuery()) {
                while (result.next()) {
                    String name = result.getString("name");
                    String path = result.getString("path");

                    Asset asset = new Asset(name, path);
                    assets.add(asset);
                }
            }
        }

        return assets;
    }
    public ArrayList<String> findWorkspaceNames() throws SQLException {
        ArrayList<String> names = new ArrayList<>();

        String sql = "SELECT name FROM workspaces ORDER BY id";
        try (Connection connection = openConnection();
             PreparedStatement query = connection.prepareStatement(sql);
             ResultSet result = query.executeQuery()) {

            while (result.next()) {
                String name = result.getString("name");
                names.add(name);
            }
        }

        return names;
    }
    public int renameAsset(int assetId, String newName) throws SQLException {
        String sql = """
            UPDATE assets
            SET name = ?
            WHERE id = ?;
            """;

        try (Connection connection = openConnection();
             PreparedStatement update = connection.prepareStatement(sql)) {
            update.setString(1,newName);
            update.setInt(2,assetId);

            return update.executeUpdate();
        }
    }
    public int renameWorkspace(int workspaceID,String newName) throws SQLException{
        String sql = """
            UPDATE workspaces
            SET name = ?
            WHERE id = ?;
            """;

        try (Connection connection = openConnection();
             PreparedStatement update = connection.prepareStatement(sql)) {
            update.setString(1,newName);
            update.setInt(2,workspaceID);

            return update.executeUpdate();
        }
    }
    public int saveAsset(Connection connection, String name, String path) throws SQLException {
        String sql = """
            INSERT INTO assets (name, path)
            VALUES (?, ?)
            ON CONFLICT(path) DO UPDATE
            SET name = excluded.name;
            """;

        try (PreparedStatement save = connection.prepareStatement(sql)) {
            save.setString(1,name);
            save.setString(2,path);

            return save.executeUpdate();
        }
    }
    public int clearWorkspaceLinks(Connection connection, int workspaceId) throws SQLException {
        String sql = """
            DELETE FROM workspace_assets
            WHERE workspace_id = ?;
            """;

        try (PreparedStatement clear = connection.prepareStatement(sql)) {
            clear.setInt(1,workspaceId);

            return clear.executeUpdate();
        }
    }
    public int addAssetToWorkspace(String workspaceName, String name, String path) throws SQLException {
        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);

            try {
                int workspaceId = findWorkspaceId(connection, workspaceName);

                addAsset(connection, name, path);
                int assetId = findAssetId(connection, path);

                int addedLinks = addAssetLink(connection, workspaceId, assetId);
                connection.commit();
                return addedLinks;
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }
    public int deleteWorkspace(String name) throws SQLException {
        String sql = "DELETE FROM workspaces WHERE id = ?";

        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);

            try {
                int workspaceId = findWorkspaceId(connection, name);
                clearWorkspaceLinks(connection, workspaceId);

                int deletedRows;
                try (PreparedStatement delete = connection.prepareStatement(sql)) {
                    delete.setInt(1, workspaceId);
                    deletedRows = delete.executeUpdate();
                }

                connection.commit();
                return deletedRows;
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }
}
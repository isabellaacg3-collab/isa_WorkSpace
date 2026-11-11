import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;

public class DatabaseTest {
    public static void main(String[] args) {
        Path file = Path.of("workspace-test.db").toAbsolutePath();
        WorkspaceDatabase database = new WorkspaceDatabase(file);

        try (Connection connection = database.openConnection();
             Statement statement = connection.createStatement()) {
            try {
                database.initialize();
            } catch (SQLException exception) {
                System.out.println("数据库初始化失败：" + exception.getMessage());
                return;
            }

            System.out.println("新增资源数：" + database.addAsset("异常处理", "doc/chapter9.pptx"));
            System.out.println("新增工作区数：" + database.addWorkspace("Java课程"));

            String querySql = "SELECT id, name, path FROM assets ORDER BY id";
            try (ResultSet result = statement.executeQuery(querySql)) {
                while (result.next()) {
                    int id = result.getInt("id");
                    String name = result.getString("name");
                    String path = result.getString("path");
                    System.out.println(id + ";" + name + ";" + path);
                }
            }

            System.out.println("连接成功：" + file);
            System.out.println("资源表已就绪");

            ArrayList<String> names = database.findWorkspaceNames();

            System.out.println("所有工作区：");
            for (String name : names) {
                System.out.println(name);
            }

            int workspaceId = database.findWorkspaceId("Java课程");
            int assetId = database.findAssetId("doc/chapter9.pptx");
            System.out.println("工作区编号：" + workspaceId);
            System.out.println("资源编号：" + assetId);
            System.out.println("新增关联数：" + database.addAssetLink(workspaceId, assetId));

            int renamedRows = database.renameAsset(assetId, "Java异常处理");
            System.out.println("改名影响行数：" + renamedRows);
            int missingRows = database.renameAsset(-1, "不该出现的名称");
            System.out.println("不存在的编号，影响行数：" + missingRows);

            ArrayList<Asset> assets = database.findWorkspaceAssets(workspaceId);
            System.out.println("当前工作区关联的资源：");
            for (Asset asset : assets) {
                System.out.println(asset.getName() + ";" + asset.getPath());
            }

            WorkspaceManager manager = new WorkspaceManager();
            manager.loadFromDatabase(database);
            Workspace restoredWorkspace = manager.findWorkspaceByName("Java课程");
            System.out.println("从数据库恢复的工作区：");
            restoredWorkspace.printAssets();

            try (Connection transaction = database.openConnection()) {
                transaction.setAutoCommit(false);
                try {
                    // ① INSERT：登记工作区和资源
                    database.addWorkspace(transaction, "事务保存练习");
                    int firstSave = database.saveAsset(transaction, "事务资料", "doc/transaction-demo.txt");
                    int secondSave = database.saveAsset(transaction, "事务资料修订版", "doc/transaction-demo.txt");
                    System.out.println("两次保存影响行数：" + firstSave + "，" + secondSave);

                    // ② SELECT：取得两条记录的编号
                    int txWorkspaceId = database.findWorkspaceId(transaction, "事务保存练习");
                    int txAssetId = database.findAssetId(transaction, "doc/transaction-demo.txt");

                    // ③ INSERT：用编号建立关联
                    database.addAssetLink(transaction, txWorkspaceId, txAssetId);

                    transaction.commit();
                    System.out.println("事务保存成功");
                } catch (SQLException exception) {
                    transaction.rollback();
                    throw exception;
                }
            }
            // 提交后重新恢复，检查最终结果
            manager.loadFromDatabase(database);
            manager.findWorkspaceByName("事务保存练习").printAssets();

            Workspace sample = manager.findWorkspaceByName("事务保存练习");
            // 修改名称，并增加一份临时资源
            sample.findAssetByName("事务资料修订版").setName("事务资料最终版");
            sample.addAsset("临时关联", "doc/temp.txt");
            manager.saveToDatabase(database);
            // 移除临时关联，再保存一次
            sample.removeAsset(sample.findAssetByName("临时关联"));
            manager.saveToDatabase(database);
            // 用全新的管理器读取数据库，检查最终结果
            WorkspaceManager verification = new WorkspaceManager();
            verification.loadFromDatabase(database);
            System.out.println("再次保存后恢复的结果：");
            verification.findWorkspaceByName("事务保存练习").printAssets();

            System.out.println("解除关联数：" + database.removeAssetLink( workspaceId, assetId));
            System.out.println("再次解除关联数：" + database.removeAssetLink( workspaceId, assetId));
            System.out.println("资源仍存在，编号：" + database.findAssetId("doc/chapter9.pptx"));
        } catch (SQLException exception) {
            System.out.println("数据库操作失败：" + exception.getMessage());
        }
    }
}
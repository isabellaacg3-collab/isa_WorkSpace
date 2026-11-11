import java.nio.file.Files;
import java.util.Scanner;
import java.nio.file.InvalidPathException;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.DirectoryStream;
import java.util.ArrayList;
import java.sql.SQLException;

public class Main {
    private static WorkspaceManager manager = new WorkspaceManager();
    private static Workspace currentWorkspace;
    private static Scanner scanner = new Scanner(System.in);
    private static Path workspaceRoot = Path.of(".").toAbsolutePath();
    private static Path databaseFile = workspaceRoot.resolve("workspace.db");
    private static WorkspaceDatabase database = new WorkspaceDatabase(databaseFile);
    private static Path currentDirectory = workspaceRoot;
    private static ArrayList<Path> listedEntries = new ArrayList<>();
    private static void printMenu() {
        System.out.println("当前工作区：" + currentWorkspace.getName());
        System.out.println("1：列出资源");
        System.out.println("2：添加资源");
        System.out.println("3：查找资源");
        System.out.println("4：移除资源");
        System.out.println("5：检查资源路径");
        System.out.println("6：重命名资源");
        System.out.println("7：保存资源");
        System.out.println("8：读取资源");
        System.out.println("9：打开资源");
        System.out.println("10、创建工作区");
        System.out.println("11、切换工作区");
        System.out.println("12：浏览当前目录");
        System.out.println("13：进入目录");
        System.out.println("14：按编号添加资源");
        System.out.println("0：退出");
        System.out.print("请输入选项：");

    }
    private static void operation(String choice){
        if (choice.equals("1")) {
            currentWorkspace.printAssets();
        } else if (choice.equals("2")) {
            System.out.print("资源名称：");
            String name = scanner.nextLine();
            if(name.isBlank()){
                System.out.println("资源名不可为空！");
                return;
            }
            if(currentWorkspace.findAssetByName(name) != null){
                System.out.println("名称已存在！");
                return;
            }
            System.out.print("资源路径：");
            String path = scanner.nextLine();
            Asset newAsset = new Asset(name, path);
            currentWorkspace.addAsset(newAsset);
            System.out.println("Add Successfully:" + newAsset.getName());
        } else if (choice.equals("3")) {
            System.out.println("查找名称：");
            String name = scanner.nextLine();
            Asset asset = currentWorkspace.findAssetByName(name);
            if (asset == null) {
                System.out.println("Not Found!");
            } else {
                System.out.println(asset.getName() + ";" + asset.getPath());
            }
        } else if (choice.equals("4")) {
            System.out.println("移除名称:");
            String name = scanner.nextLine();
            Asset asset = currentWorkspace.findAssetByName(name);
            if (asset == null) {
                System.out.println("Not Found!");
            } else {
                if (currentWorkspace.removeAsset(asset)) {
                    System.out.println("已移除：" + asset.getName() + ";" + asset.getPath());
                } else {
                    System.out.println("移除失败");
                }
            }
        } else if (choice.equals("5")) {
            System.out.println("检查名称：");
            String name = scanner.nextLine();
            Asset asset = currentWorkspace.findAssetByName(name);
            if (asset == null) {
                System.out.println("Not Found!");
            } else {
                try {
                    if (asset.exists(workspaceRoot)) {
                        System.out.println("通过检查：" + asset.getName() + ";" + asset.getPath());
                    } else {
                        System.out.println("路径不存在或无法确认其存在");
                    }
                } catch (InvalidPathException exception) {
                    System.out.println("非法路径" + exception.getMessage());
                }
            }
        } else if (choice.equals("6")) {
            System.out.println("重命名资源：");
            String name = scanner.nextLine();
            Asset asset = currentWorkspace.findAssetByName(name);
            if(asset == null){
                System.out.println("Not found!");
                return;
            }
            System.out.println("重命名：");
            String newName = scanner.nextLine();
            if(newName.isBlank()){
                System.out.println("名称不可为空！");
                return;
            }
            if(currentWorkspace.findAssetByName(newName) != null){
                System.out.println("名称已存在！");
                return;
            }
            asset.setName(newName);
            System.out.println("重命名成功！");
        } else if (choice.equals("7")) {
            try {
                manager.saveToDatabase(database);
                System.out.println("已保存到：" + databaseFile.toAbsolutePath());
            } catch (SQLException exception) {
                System.out.println("保存失败：" + exception.getMessage());
            }
        } else if (choice.equals("8")) {
            try {
                restoreWorkspaces(database);
                System.out.println("已恢复全部工作区");
            } catch (SQLException exception) {
                System.out.println("恢复失败：" + exception.getMessage());
            }
        }else if (choice.equals("9")) {
            System.out.println("要打开的资源名称");
            String name = scanner.nextLine();
            Asset asset = currentWorkspace.findAssetByName(name);
            if(asset == null){
                System.out.println("资源未找到");
                return;
            }
            try{
                asset.open(workspaceRoot);
            } catch (IOException | InvalidPathException exception) {
                System.out.println("打开失败：" + exception.getMessage());
            }
        } else if (choice.equals("10")) {
            System.out.print("新工作区名称：");
            String name = scanner.nextLine();
            if(name.isBlank()){
                System.out.println("名称不可为空");
                return;
            }
            if(manager.createWorkspace(name)){
                System.out.println("工作区创建成功");
            }else {
                System.out.println("工作区已创建");
            }
        } else if (choice.equals("11")) {
            System.out.println("目标工作区");
            String name = scanner.nextLine();
            Workspace target = manager.findWorkspaceByName(name);
            if(target == null){
                System.out.println("工作区不存在");
                return;
            }
            currentWorkspace = target;
        } else if (choice.equals("12")) {
            try{
                listedEntries = browseDirectory(currentDirectory);
            } catch (IOException exception) {
                System.out.println("浏览失败：" + exception.getMessage());
            }
        } else if (choice.equals("13")) {
            System.out.print("目标目录（输入 .. 返回上级）：");
            String input = scanner.nextLine();

            try {
                Path target = currentDirectory.resolve(input).normalize();
                if (!Files.isDirectory(target)) {
                    System.out.println("目标不是可访问的目录");
                    return;
                }
                listedEntries = browseDirectory(target);
                currentDirectory = target;
            } catch (IOException | InvalidPathException exception) {
                System.out.println("进入失败：" + exception.getMessage());
            }
        } else if (choice.equals("14")) {
            if (listedEntries.isEmpty()) {
                System.out.println("请先浏览一个有内容的目录");
                return;
            }

            System.out.print("资源编号：");

            try {
                int number = Integer.parseInt(scanner.nextLine());
                if(number < 1 || number > listedEntries.size()){
                    System.out.println("编号越界");
                    return;
                }

                Path selected = listedEntries.get(number - 1);
                addBrowsedAsset(selected);

            } catch (NumberFormatException exception) {
                System.out.println("编号必须是有效整数");
            }
        } else {
            System.out.println("无效命令!");
        }
    }
    private static void restoreWorkspaces(Path file) throws IOException {
        String previousName = currentWorkspace.getName();

        manager.loadAll(file);

        manager.createWorkspace("Java Workspace");
        currentWorkspace = manager.findWorkspaceByName(previousName);

        if (currentWorkspace == null) {
            currentWorkspace = manager.findWorkspaceByName("Java Workspace");
        }
    }
    private static void restoreWorkspaces(WorkspaceDatabase database) throws SQLException {
        String previousName = currentWorkspace.getName();

        manager.loadFromDatabase(database);

        manager.createWorkspace("Java Workspace");
        currentWorkspace = manager.findWorkspaceByName(previousName);

        if (currentWorkspace == null) {
            currentWorkspace = manager.findWorkspaceByName("Java Workspace");
        }
    }
    private static ArrayList<Path> browseDirectory(Path directory) throws IOException {
        ArrayList<Path> result = new ArrayList<>();

        try (DirectoryStream<Path> entries =
                     Files.newDirectoryStream(directory)) {
            for (Path entry : entries) {
                result.add(entry);
                if(Files.isDirectory(entry)){
                    System.out.println(result.size() + "[目录]" + entry.getFileName());
                }else {
                    System.out.println(result.size() + "[文件]" + entry.getFileName());
                }
            }
        }

        return result;
    }
    private static void addBrowsedAsset(Path selected) {
        selected = selected.toAbsolutePath().normalize();

        if (!Files.exists(selected)) {
            System.out.println("资源已不存在或无法访问，请重新浏览");
            return;
        }

        String name = selected.getFileName().toString();
        if(currentWorkspace.findAssetByName(name) != null){
            System.out.println("已有同名资源！");
            return;
        }

        Path root = workspaceRoot.toAbsolutePath().normalize();
        String storedPath;

        if (selected.startsWith(root)) {
            storedPath = root.relativize(selected).toString();
        } else {
            storedPath = selected.toString();
        }

        currentWorkspace.addAsset(name,storedPath);

        System.out.println("已加入当前工作区：" + name);
    }
    public static void main(String[] args) {
        manager.createWorkspace("Java Workspace");
        currentWorkspace = manager.findWorkspaceByName("Java Workspace");

        try {
            database.initialize();
            restoreWorkspaces(database);
            System.out.println("工作台已就绪：" + databaseFile.toAbsolutePath());
        } catch (SQLException exception) {
            System.out.println("启动失败：" + exception.getMessage());
            return;
        }

        while (true){
            printMenu();
            String choice = scanner.nextLine();
            if(choice.equals("0")){
                break;
            }
            operation(choice);
        }
    }
}
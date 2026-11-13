import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.scene.control.ListView;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import javafx.scene.control.ListCell;
import javafx.scene.layout.HBox;
import java.io.IOException;
import java.nio.file.InvalidPathException;
import javafx.stage.FileChooser;
import java.io.File;
import javafx.scene.control.TextInputDialog;
import java.util.List;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.stage.DirectoryChooser;
import java.util.function.Consumer;
import java.net.URISyntaxException;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;

public class WorkspaceFx extends Application {
    @Override
    public void start(Stage stage) throws URISyntaxException {
        Path databaseFile = resolveWorkspaceRoot().resolve("workspace.db");
        WorkspaceDatabase database = new WorkspaceDatabase(databaseFile);

        Label label = new Label();
        Button button = new Button("刷新工作区");
        ListView<String> workspaceList = new ListView<>();
        workspaceList.setPrefHeight(180);
        button.setOnAction(event -> {
            if (!Files.isRegularFile(databaseFile)) {
                label.setText("找不到 workspace.db，请检查工作目录");
                return;
            }

            try {
                List<String> names = database.findWorkspaceNames();
                String previousName = workspaceList.getSelectionModel().getSelectedItem();

                workspaceList.getSelectionModel().clearSelection();
                workspaceList.getItems().setAll(names);
                label.setText("已读取 " + names.size() + " 个工作区");

                if (previousName != null && names.contains(previousName)) {
                    workspaceList.getSelectionModel().select(previousName);
                } else {
                    workspaceList.getSelectionModel().selectFirst();
                }
            } catch (SQLException exception) {
                label.setText("读取失败：" + exception.getMessage());
            }
        });

        ListView<Asset> assetList = new ListView<>();
        assetList.setPrefHeight(180);
        assetList.setCellFactory(list -> new ListCell<Asset>() {
            @Override
            protected void updateItem(Asset asset, boolean empty) {
                super.updateItem(asset, empty);
                setText(empty || asset == null ? null : asset.getName());
            }
        });

        workspaceList.getSelectionModel().selectedItemProperty()
                .addListener((observable, oldName, selectedName) -> {
                    assetList.getItems().clear();
                    if (selectedName == null) {
                        return;
                    }

                    try {
                        int workspaceId = database.findWorkspaceId(selectedName);
                        assetList.getItems().setAll(database.findWorkspaceAssets(workspaceId));
                        int assetCount = assetList.getItems().size();

                        label.setText(selectedName + " 有 " + assetCount + " 项资源");
                    } catch (SQLException exception) {
                        label.setText("读取资源失败：" + exception.getMessage());
                    }
                });

        Button openButton = new Button("打开资源");
        openButton.disableProperty().bind(
                assetList.getSelectionModel().selectedItemProperty().isNull()
        );
        openButton.setOnAction(event -> {
            Asset asset = assetList.getSelectionModel().getSelectedItem();

            try {
                asset.open(databaseFile.getParent());
                label.setText("已打开 " + asset.getName());
            } catch (IOException | InvalidPathException exception) {
                label.setText("打开失败：" + exception.getMessage());
            }
        });

        Button deleteWorkspaceButton = new Button("删除工作区");
        deleteWorkspaceButton.disableProperty().bind(
                workspaceList.getSelectionModel().selectedItemProperty().isNull()
        );
        deleteWorkspaceButton.setOnAction(event -> {
            String name = workspaceList.getSelectionModel().getSelectedItem();
            Alert confirm = new Alert(
                    Alert.AlertType.CONFIRMATION,
                    "删除「" + name + "」及其所有资源关联？",
                    ButtonType.OK, ButtonType.CANCEL
            );
            confirm.initOwner(stage);
            confirm.setTitle("删除工作区");
            confirm.setHeaderText(null);

            if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
                return;
            }

            try {
                database.deleteWorkspace(name);
                workspaceList.getSelectionModel().clearSelection();
                workspaceList.getItems().remove(name);
                label.setText("已删除工作区 " + name);
            } catch (SQLException exception) {
                label.setText("删除失败：" + exception.getMessage());
            }
        });

        Consumer<Path> registerPath = chosenPath -> {
            Path selectedPath = chosenPath.toAbsolutePath().normalize();
            Path root = databaseFile.getParent().normalize();
            Path sharedRoot = root.resolve("..").normalize();

            String storedPath = selectedPath.startsWith(sharedRoot)
                    ? root.relativize(selectedPath).toString()
                    : selectedPath.toString();

            if (storedPath.isEmpty()) {
                storedPath = ".";
            }

            Path fileName = selectedPath.getFileName();
            String assetName = fileName == null
                    ? selectedPath.toString()
                    : fileName.toString();
            String workspaceName = workspaceList.getSelectionModel().getSelectedItem();

            int addedLinks;
            try {
                addedLinks = database.addAssetToWorkspace(
                        workspaceName, assetName, storedPath);
            } catch (SQLException exception) {
                label.setText("添加失败：" + exception.getMessage());
                return;
            }

            try {
                int workspaceId = database.findWorkspaceId(workspaceName);
                assetList.getItems().setAll(database.findWorkspaceAssets(workspaceId));
                label.setText(addedLinks == 1 ? "已添加 " + assetName : "此文件已在当前工作区");
            } catch (SQLException exception) {
                label.setText("资源已登记，列表刷新失败：" + exception.getMessage());
            }
        };
        MenuItem fileItem = new MenuItem("文件");
        MenuItem directoryItem = new MenuItem("文件夹");
        MenuItem pathItem = new MenuItem("完整路径");
        MenuButton chooseButton = new MenuButton("添加资源", null, fileItem, directoryItem, pathItem);
        chooseButton.disableProperty().bind(
                workspaceList.getSelectionModel().selectedItemProperty().isNull()
        );
        fileItem.setOnAction(event -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("选择文件");
            File selected = chooser.showOpenDialog(stage);
            if (selected != null) {
                registerPath.accept(selected.toPath());
            }
        });
        directoryItem.setOnAction(event -> {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle("选择文件夹");
            File selected = chooser.showDialog(stage);
            if (selected != null) {
                registerPath.accept(selected.toPath());
            }
        });
        pathItem.setOnAction(event -> {
            TextInputDialog dialog = new TextInputDialog();
            dialog.initOwner(stage);
            dialog.setTitle("完整路径");
            dialog.setHeaderText("可粘贴文件、目录或 .lnk 快捷方式的完整路径");
            dialog.setContentText("路径：");

            dialog.showAndWait().ifPresent(text -> {
                String input = text.strip();
                if (input.length() >= 2 && input.startsWith("\"") && input.endsWith("\"")) {
                    input = input.substring(1, input.length() - 1);
                }

                try {
                    Path path = Path.of(input);
                    if (!path.isAbsolute()) {
                        label.setText("请输入完整路径");
                    } else if (!Files.exists(path)) {
                        label.setText("路径不存在或无法访问：" + path);
                    } else {
                        registerPath.accept(path);
                    }
                } catch (InvalidPathException exception) {
                    label.setText("路径格式无效：" + exception.getInput());
                }
            });
        });

        Button createButton = new Button("新建工作区");
        createButton.setOnAction(event -> {
            TextInputDialog dialog = new TextInputDialog();
            dialog.initOwner(stage);
            dialog.setTitle("新建工作区");
            dialog.setHeaderText(null);
            dialog.setContentText("名称");

            dialog.showAndWait().ifPresent(text -> {
                String name = text.strip();
                if (name.isEmpty()) {
                    label.setText("工作区名称不可为空");
                    return;
                }

                try {
                    database.initialize();

                    if (database.addWorkspace(name) == 0) {
                        label.setText("工作区已存在");
                        return;
                    }

                    workspaceList.getItems().add(name);
                    workspaceList.getSelectionModel().select(name);
                    label.setText("已创建 " + name);
                } catch (SQLException exception) {
                    label.setText("创建失败：" + exception.getMessage());
                }
            });
        });

        Button renameButton = new Button("重命名工作区");
        renameButton.disableProperty().bind(
                workspaceList.getSelectionModel().selectedItemProperty().isNull()
        );
        renameButton.setOnAction(event -> {
            String oldName = workspaceList.getSelectionModel().getSelectedItem();
            int selectedIndex = workspaceList.getSelectionModel().getSelectedIndex();

            TextInputDialog dialog = new TextInputDialog(oldName);
            dialog.initOwner(stage);
            dialog.setTitle("重命名工作区");
            dialog.setHeaderText(null);
            dialog.setContentText("名称");

            dialog.showAndWait().ifPresent(text -> {
                String newName = text.strip();
                if (newName.isEmpty()) {
                    label.setText("工作区名称不可为空");
                    return;
                }
                if (newName.equals(oldName)) {
                    label.setText("名称重复");
                    return;
                }
                if (workspaceList.getItems().contains(newName)) {
                    label.setText("工作区已存在");
                    return;
                }

                try {
                    int workspaceId = database.findWorkspaceId(oldName);
                    if (database.renameWorkspace(workspaceId, newName) == 0) {
                        label.setText("找不到要重命名的工作区");
                        return;
                    }

                    workspaceList.getSelectionModel().clearSelection();
                    workspaceList.getItems().set(selectedIndex, newName);
                    workspaceList.getSelectionModel().select(selectedIndex);
                    label.setText("已重命名 " + newName);
                } catch (SQLException exception) {
                    label.setText("重命名失败：" + exception.getMessage());
                }
            });
        });

        Button renameAssetButton = new Button("重命名资源");
        renameAssetButton.disableProperty().bind(
                assetList.getSelectionModel().selectedItemProperty().isNull()
        );
        renameAssetButton.setOnAction(event -> {
            Asset asset = assetList.getSelectionModel().getSelectedItem();
            TextInputDialog dialog = new TextInputDialog(asset.getName());

            dialog.initOwner(stage);
            dialog.setTitle("重命名资源");
            dialog.setHeaderText(null);
            dialog.setContentText("名称");

            dialog.showAndWait().ifPresent(text -> {
                String newName = text.strip();
                if (newName.isEmpty()) {
                    label.setText("资源名称不可为空");
                    return;
                }

                try {
                    int assetId = database.findAssetId(asset.getPath());
                    if (database.renameAsset(assetId, newName) == 1) {
                        asset.setName(newName);
                        assetList.refresh();
                        label.setText("已重命名 " + newName);
                    } else {
                        label.setText("找不到要重命名的资源");
                    }
                } catch (SQLException exception) {
                    label.setText("重命名失败：" + exception.getMessage());
                }
            });
        });

        Button removeButton = new Button("移除关联");
        removeButton.disableProperty().bind(
                assetList.getSelectionModel().selectedItemProperty().isNull()
        );
        removeButton.setOnAction(event -> {
            String workspaceName = workspaceList.getSelectionModel().getSelectedItem();
            Asset asset = assetList.getSelectionModel().getSelectedItem();

            try {
                int workspaceId = database.findWorkspaceId(workspaceName);
                int assetId = database.findAssetId(asset.getPath());

                int removedLinks = database.removeAssetLink(workspaceId, assetId);
                assetList.getItems().remove(asset);

                label.setText(removedLinks == 1
                        ? "已移除关联 " + asset.getName()
                        : "这条关联已不存在");
            } catch (SQLException exception) {
                label.setText("移除失败：" + exception.getMessage());
            }
        });

        Button locateButton = new Button("打开所在目录");
        locateButton.disableProperty().bind(
                assetList.getSelectionModel().selectedItemProperty().isNull()
        );
        locateButton.setOnAction(event -> {
            Asset asset = assetList.getSelectionModel().getSelectedItem();

            try {
                asset.locate(databaseFile.getParent());
                label.setText("已请求定位" + asset.getName());
            } catch (IOException | InvalidPathException exception) {
                label.setText("定位失败：" + exception.getMessage());
            }
        });

        Label previewLabel = new Label("请选择资源");
        previewLabel.setWrapText(true);
        ListView<String> directoryList = new ListView<>();
        directoryList.setPrefHeight(120);
        directoryList.setMaxHeight(120);
        directoryList.setMinHeight(0);
        directoryList.setVisible(false);
        directoryList.managedProperty().bind(directoryList.visibleProperty());
        VBox previewPane = new VBox(6, new Label("预览"), previewLabel, directoryList);
        previewPane.setPrefWidth(320);
        assetList.getSelectionModel().selectedItemProperty()
                .addListener((observable, oldAsset, selectedAsset) -> {
                    directoryList.getItems().clear();
                    directoryList.setVisible(false);

                    if (selectedAsset == null) {
                        previewLabel.setText("请选择资源");
                        return;
                    }
                    Path path = selectedAsset.resolvePath(databaseFile.getParent()).toAbsolutePath().normalize();
                    if(!Files.exists(path)){
                        previewLabel.setText("路径不存在或无法访问");
                    } else if (Files.isDirectory(path)) {
                        previewLabel.setText("名称：" + selectedAsset.getName()
                                + "\n路径：" + path
                                + "\n类型：文件夹");

                        directoryList.setVisible(true);
                        directoryList.setPlaceholder(new Label("空文件夹"));

                        try (var children = Files.list(path)) {
                            directoryList.getItems().setAll(
                                    children.map(entry -> entry.getFileName().toString()).toList()
                            );
                        } catch (IOException | java.io.UncheckedIOException exception) {
                            directoryList.setPlaceholder(new Label("无法读取目录内容"));
                        }
                    }else {
                        String fileName = path.getFileName().toString();
                        String type = "文件";
                        int dot = fileName.lastIndexOf('.');
                        if(dot > 0 && dot < fileName.length() - 1){
                            type = fileName.substring(dot);
                        }
                        previewLabel.setText("名称：" + selectedAsset.getName()
                                + "\n路径：" + path
                                + "\n类型：" + type
                        );
                    }
                });

        HBox actionsUp = new HBox(12,
                button,
                createButton,
                renameButton,
                deleteWorkspaceButton
        );
        actionsUp.setAlignment(Pos.CENTER_LEFT);


        HBox actionsDown = new HBox(12,
                chooseButton,
                openButton,
                locateButton,
                renameAssetButton,
                removeButton
        );
        actionsDown.setAlignment(Pos.CENTER_LEFT);

        HBox lists = new HBox(12,
                new VBox(6, new Label("工作区"), workspaceList),
                new VBox(6, new Label("资源"), assetList),
                previewPane
        );

        VBox root = new VBox(12, actionsUp, lists, actionsDown,label);
        root.setAlignment(Pos.CENTER);
        Scene scene = new Scene(root, 854, 480);

        stage.setTitle("isa Workspace");
        stage.setScene(scene);
        stage.show();
        button.fire();
    }
    private static Path resolveWorkspaceRoot() throws URISyntaxException {
        Path codeLocation = Path.of(
                WorkspaceFx.class.getProtectionDomain()
                        .getCodeSource()
                        .getLocation()
                        .toURI()
        ).toAbsolutePath().normalize();

        if (Files.isRegularFile(codeLocation)) {
            return codeLocation.getParent();
        }

        return Path.of(".").toAbsolutePath().normalize();
    }
    public static void main(String[] args) {
        launch(args);
    }
}
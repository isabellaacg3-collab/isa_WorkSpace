import java.nio.file.Path;
import java.nio.file.Files;
import java.awt.Desktop;
import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class Asset {
    private String name;
    private String path;
    public Asset(){
        /*this.name = "";
        this.path = "";*/
        this("","");
    }
    public Asset(String name,String path){
        this.name = name;
        this.path = path;
    }
    public String getName(){
        return this.name;
    }
    public String getPath(){
        return this.path;
    }
    public void setName(String name){
        this.name = name;
    }
    public void setPath(String path){
        this.path = path;
    }
    public boolean exists(Path root){
        if(this.path == null || this.path.isBlank()){
            return false;
        }
        Path filePath = resolvePath(root);
        return Files.exists(filePath);
    }
    public void open(Path root) throws IOException {
        if (!this.exists(root)) {
            throw new IOException("路径不存在或无法确认其存在");
        }

        Path file = resolvePath(root);
        Path fileName = file.getFileName();
        if (System.getProperty("os.name").startsWith("Windows")
                && fileName != null && fileName.toString().toLowerCase(Locale.ROOT).endsWith(".lnk")
                && Files.isRegularFile(file)) {
            openWindowsShortcut(file.toAbsolutePath().normalize());
            return;
        }

        Desktop.getDesktop().open(file.toFile());
    }
    public void locate(Path root) throws IOException {
        if (!this.exists(root)) {
            throw new IOException("路径不存在或无法确认其存在");
        }

        Path file = resolvePath(root).toAbsolutePath().normalize();

        if(file.getParent() == null){
            throw new IOException("此路径没有可供定位的上一级目录");
        }

        new ProcessBuilder("explorer.exe", "/select,", file.toString()).start();
    }
    private static void openWindowsShortcut(Path shortcut) throws IOException {
        String systemRoot = System.getenv("SystemRoot");
        if (systemRoot == null || systemRoot.isBlank()) {
            throw new IOException("找不到 Windows PowerShell");
        }

        Path powershell = Path.of(systemRoot, "System32", "WindowsPowerShell", "v1.0", "powershell.exe");
        ProcessBuilder launcher = new ProcessBuilder(powershell.toString(),
                "-NoProfile", "-NonInteractive", "-WindowStyle", "Hidden", "-Command",
                "$ErrorActionPreference = 'Stop'; Start-Process -FilePath $env:ISA_WORKSPACE_SHORTCUT -Verb Open");
        // Pass the literal path separately so PowerShell cannot interpret it as code.
        launcher.environment().put("ISA_WORKSPACE_SHORTCUT", shortcut.toString());
        launcher.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        launcher.redirectError(ProcessBuilder.Redirect.DISCARD);

        Process process = launcher.start();
        try {
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IOException("Windows Shell 打开快捷方式超时：" + shortcut);
            }
            if (process.exitValue() != 0) {
                throw new IOException("Windows Shell 无法打开快捷方式：" + shortcut);
            }
        } catch (InterruptedException exception) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new IOException("打开快捷方式时被中断：" + shortcut, exception);
        }
    }
    public Path resolvePath(Path root) {
        Path storedPath = Path.of(this.path);

        if (storedPath.isAbsolute()) {
            return storedPath;
        }

        return root.resolve(storedPath);
    }
}

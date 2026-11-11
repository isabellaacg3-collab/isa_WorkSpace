import java.nio.file.Path;
import java.nio.file.Files;
import java.awt.Desktop;
import java.io.IOException;

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

        Desktop.getDesktop().open(resolvePath(root).toFile());
    }
    public Path resolvePath(Path root) {
        Path storedPath = Path.of(this.path);

        if (storedPath.isAbsolute()) {
            return storedPath;
        }

        return root.resolve(storedPath);
    }
}

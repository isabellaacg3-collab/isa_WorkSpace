import java.util.ArrayList;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class Workspace {
    private String name;
    private ArrayList<Asset> assets = new ArrayList<>();
    public Workspace(String name) {
        this.name = name;
    }
    public void addAsset(Asset asset) {

        assets.add(asset);
    }
    public void addAsset(String name,String path){
        Asset newAsset = new Asset(name,path);
        addAsset(newAsset);
    }
    public ArrayList<Asset> getAssets() {
        return new ArrayList<>(this.assets);
    }
    public int getAssetCount() {

        return assets.size();
    }
    public void printAssets(){
        System.out.println("工作区" + this.name);
        for (Asset asset : this.assets){
            System.out.println(asset.getName() + ";" + asset.getPath());
        }
    }
    public Asset findAssetByName(String name){
        for(Asset asset : assets){
            if(asset.getName().equals(name)){
                return asset;
            }
        }
        return null;
    }
    public boolean removeAsset(Asset asset){
       return assets.remove(asset);
    }
    public void saveTo(Path file) throws IOException {
        Files.write(file, exportAssetLines(), StandardCharsets.UTF_8);
    }
    public void loadFrom(Path file) throws IOException {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);

        if (lines.size() % 2 != 0) {
            throw new IOException("数据不完整：每份资源应占两行");
        }

        ArrayList<Asset> loadedAssets = new ArrayList<>();

        for (int i = 0; i < lines.size(); i += 2) {
            String name = lines.get(i);
            String path = lines.get(i + 1);
            loadedAssets.add(new Asset(name,path));
        }

        this.assets = loadedAssets;
    }
    public String getName() {
        return this.name;
    }
    public ArrayList<String> exportAssetLines() {
        ArrayList<String> lines = new ArrayList<>();

        for (Asset asset : this.assets) {
            lines.add(asset.getName());
            lines.add(asset.getPath());
        }

        return lines;
    }
}
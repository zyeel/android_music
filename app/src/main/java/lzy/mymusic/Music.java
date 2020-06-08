package lzy.mymusic;

/**
 * Created by lzyest on 2020/5/31.
 */

public class Music {
    public int ID;
    public String Title;
    public String Artist;
    public String Album;
    public int AlbumID;
    public String Url;
    public int Duration;
    public long Size;

    public void setID(int id){
        ID = id;
    }
    public void setTitle(String title){
        Title = title;
    }
    public void setArtist(String artist){
        Artist = artist;
    }
    public void setAlbum(String album){
        Album = album;
    }
    public void setAlbumID(int albumID){
        AlbumID = albumID;
    }
    public void setUrl(String url){
        Url = url;
    }
    public void setDuration(int duration){
        Duration = duration;
    }
    public void setSize(long size){
        Size = size;
    }
    public String getUrl(){
        return Url;
    }
    public int getDuration(){
        return Duration;
    }
}

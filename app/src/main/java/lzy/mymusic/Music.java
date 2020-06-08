package lzy.mymusic;

//歌曲信息
class Music {
    private String Url;     //路径
    private int Duration;   //持续时间

    void setUrl(String url){
        Url = url;
    }
    void setDuration(int duration){
        Duration = duration;
    }
    String getUrl(){
        return Url;
    }
    int getDuration(){
        return Duration;
    }
}

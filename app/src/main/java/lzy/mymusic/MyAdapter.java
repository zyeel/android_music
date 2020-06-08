package lzy.mymusic;

import android.content.Context;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.List;

//自定义Adapter
class MyAdapter extends BaseAdapter{
    private Context context;
    private List<String> list = null;
    private int isPlaying;

    MyAdapter(Context context, List<String> list) {
        this.context = context;
        this.list = list;
    }

    @Override
    public int getCount() {
        return list.size();
    }

    @Override
    public Object getItem(int i) {
        return list.get(i);
    }

    @Override
    public long getItemId(int i) {
        return i;
    }

    @Override
    public View getView(int i, View view, ViewGroup viewGroup) {
        ViewHolder holder = null;
        if (view == null) {
            holder = new ViewHolder();
            view = View.inflate(context, R.layout.music_item, null);
            holder.song = (TextView) view.findViewById(R.id.songName);
            view.setTag(holder);
        }else{
            holder = (ViewHolder) view.getTag();
        }
        holder.song.setText(list.get(i));

        //当前播放的歌曲的颜色与其他的不一样
        if(isPlaying == i){
            holder.song.setTextColor(Color.parseColor("#33CCFF"));
        }else{
            holder.song.setTextColor(Color.parseColor("#778899"));
        }
        return view;
    }

    //获取当前播放的歌曲index
    void setPlay(int position){
        this.isPlaying = position;
    }

    private class ViewHolder{
        TextView song;
    }

}

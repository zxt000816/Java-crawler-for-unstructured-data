package model;

/**
 * Created by DALAB1 on 2018-08-08.
 */
public class Tweet {
    private String id;
    private String permalink;
    private String username;
    private String text;
    private String date;
    private int retweets;
    private int comments;
    private int favorites;
    private String mentions;
    private String hashtags;
    private String geo;

    public Tweet(){
    }

    public String getId(){return this.id;}
    public void setId(String id){this.id = id;};

    public String getPermalink(){return this.permalink;}
    public void setPermalink(String permalink){this.permalink = permalink;}

    public String getUsername(){return this.username;}
    public void setUsername(String username){this.username = username;}

    public String getText(){return this.text;}
    public void setText(String text){this.text = text;}

    public String getDate(){return this.date;}
    public void setDate(String date){this.date = date;}

    public int getRetweets(){return this.retweets;}
    public void setRetweets(int retweets){this.retweets = retweets;}

    public int getComments(){return this.comments;}
    public void setComments(int comments){this.comments = comments;}

    public int getFavorites(){return this.favorites;}
    public void setFavorites(int favorites){this.favorites = favorites;}

    public String getMentions(){return this.mentions;}
    public void setMentions(String mentions){this.mentions = mentions;}

    public String getHashtags(){return this.hashtags;}
    public void setHashtags(String hashtags){this.hashtags = hashtags;}

    public String getGeo(){return this.geo;}
    public void setGeo(String geo){this.geo = geo;}
}

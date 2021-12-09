package utils;

/**
 * Created by DALAB1 on 2018-08-08.
 */
public class TweetCriteria {
    private String username;
    private String startdate;
    private String enddate;
    private String keyword;
    private int maxTweets;

    private TweetCriteria(){}

    public static TweetCriteria create(){
        return new TweetCriteria();
    }
    public TweetCriteria setUsername(String username){
        this.username = username;
        return this;
    }
    public TweetCriteria setStartdate(String startdate){
        this.startdate = startdate;
        return this;
    }
    public TweetCriteria setEnddate(String enddate){
        this.enddate = enddate;
        return this;
    }
    public TweetCriteria setKeyword(String keyword){
        this.keyword = keyword;
        return this;
    }
    public TweetCriteria setMaxTweets(int maxTweets){
        this.maxTweets = maxTweets;
        return this;
    }

    String getUsername(){return this.username;}
    String getStartdate(){return this.startdate;}
    String getEnddate(){return this.enddate;}
    String getKeyword(){return this.keyword;}
    int getMaxTweets(){return this.maxTweets;}
}

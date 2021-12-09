package utils;

import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import model.Tweet;
import org.apache.http.HttpEntity;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import twitter4j.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

/**
 * Created by DALAB1 on 2018-08-08.
 */
public class TweetManager {
    private static final HttpClient defaultHttpClient = HttpClients.createDefault();
    static{
        Logger.getLogger("org.apache.http").setLevel(Level.OFF);
    }

    private static String getURLResponse(String startdate, String enddate, String keyword, String scrollCursor) throws Exception{
        String appendQuery = "";

        if(keyword != null){
            appendQuery = appendQuery + keyword;
        }
        if(startdate != null){
            appendQuery += " since:" + startdate;
        }
        if(enddate != null){
            appendQuery += " until:" + enddate;
        }

        String url = String.format("https://twitter.com/i/search/timeline?&q=%s&src=typd&max_position=%s", URLEncoder.encode(appendQuery, "UTF-8"), scrollCursor);
        System.out.println("URL: " + url);

        HttpGet httpGet = new HttpGet(url);
        HttpEntity resp = defaultHttpClient.execute(httpGet).getEntity();

        return EntityUtils.toString(resp);
    }

    public static List<Tweet> getTweets(TweetCriteria criteria){
        List<Tweet> results = new ArrayList<>();

        try{
            String refreshCursor = null;
            outerLace: while(true){
                JSONObject json = new JSONObject(getURLResponse(criteria.getStartdate(), criteria.getEnddate(), criteria.getKeyword(), refreshCursor));
                refreshCursor = json.getString("min_position");
                Document doc = Jsoup.parse((String)json.get("items_html"));
                Elements tweets = doc.select("div.js-stream-tweet");

                if(tweets.size() == 0){
                    break;
                }

                for(Element tweet : tweets){
                    String usernameTweet = tweet.select("span.username.u-dir.u-textTruncate b").text();
                    String txt = tweet.select("p.js-tweet-text").text().replaceAll("[^\\u0000-\\uFFFF]", "");
                    int retweets = Integer.valueOf(tweet.select("span.ProfileTweet-action--retweet span.ProfileTweet-actionCount").attr("data-tweet-stat-count").replaceAll(",", ""));
                    int favorites = Integer.valueOf(tweet.select("span.ProfileTweet-action--favorite span.ProfileTweet-actionCount").attr("data-tweet-stat-count").replaceAll(",", ""));
                    int comments = Integer.valueOf(tweet.select("span.ProfileTweet-action--reply span.ProfileTweet-actionCount").attr("data-tweet-stat-count").replaceAll(",", ""));
                    long dateMs = Long.valueOf(tweet.select("small.time span.js-short-timestamp").attr("data-time-ms"));
                    String id = tweet.attr("data-tweet-id");
                    String permalink = tweet.attr("data-permalink-path");

                    String geo = "";
                    Elements geoElement = tweet.select("span.Tweet-geo");
                    if (geoElement.size() > 0) {
                        geo = geoElement.attr("title");
                    }

                    Date date = new Date(dateMs);
                    TimeZone tz = TimeZone.getTimeZone("America/Los_Angeles");

                    SimpleDateFormat dt_format = new SimpleDateFormat("yyyy-MM-dd hh:mm");
                    dt_format.setTimeZone(tz);

                    Tweet t = new Tweet();
                    t.setId(id);
                    t.setPermalink("https://twitter.com"+permalink);
                    t.setUsername(usernameTweet);
                    t.setText(txt);
                    t.setDate(dt_format.format(date));
                    t.setRetweets(retweets);
                    t.setFavorites(favorites);
                    t.setComments(comments);
                    t.setMentions(processTerms("(@\\w*)", txt));
                    t.setHashtags(processTerms("(#\\w*)", txt));
                    t.setGeo(geo);

                    results.add(t);

                    System.out.println("Permalink: " + t.getPermalink());
                    System.out.println("Date: " + t.getDate());

                    if (criteria.getMaxTweets() > 0 && results.size() >= criteria.getMaxTweets()) {
                        break outerLace;
                    }
                }
            }
        }catch (Exception e){
            e.printStackTrace();
        }
        return results;
    }

    private static String processTerms(String patternS, String tweetText) {
        StringBuilder sb = new StringBuilder();
        Matcher matcher = Pattern.compile(patternS).matcher(tweetText);
        while (matcher.find()) {
            sb.append(matcher.group());
            sb.append(" ");
        }

        return sb.toString().trim();
    }
}

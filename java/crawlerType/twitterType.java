package crawlerType;

import com.mongodb.BasicDBObject;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import crawlerConfig.crawlerDAO;
import crawlerConfig.crawlerState;
import model.Tweet;
import utils.TweetCriteria;
import utils.TweetManager;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Created by DALAB1 on 2018-04-23.
 */
public class twitterType implements generalType {
    private crawlerState state;
    private crawlerDAO DB;
    private HashMap<String, ArrayList<String>> indexing;
    private String indexingDT;

    @Override
    public void setState(crawlerState state) {
        this.state = state;
    }

    @Override
    public void run() {
        TweetCriteria criteria = null;

        int startYear = Integer.parseInt(this.state.getStartDate().split("-")[0]);
        this.DB = new crawlerDAO();
        this.DB.setCollection(startYear + "Twitter");
        this.indexing = constructHashIndex(startYear);

        LocalDate localStart = LocalDate.parse(this.state.getStartDate());
        LocalDate localEnd = LocalDate.parse(this.state.getEndDate()).plusDays(1);

        while(localStart.isBefore(localEnd)){
            if(!this.indexingDT.equals(localStart.toString().split("-")[0])){
                startYear = Integer.parseInt(localStart.toString().split("-")[0]);
                this.DB.setCollection(startYear + "Twitter");
                this.indexing.clear();
                this.indexing = constructHashIndex(startYear);
            }

            try{
                criteria = TweetCriteria.create()
                        .setKeyword(state.getItem().getSearch_keyword())
                        .setStartdate(localStart.toString())
                        .setEnddate(localStart.plusDays(1).toString());
                List<Tweet> resultSet = TweetManager.getTweets(criteria);
                System.out.println("[" + localStart.toString() + "] Counts: " + resultSet.size());

                for(Tweet t : resultSet){
                    if(hasDatabaseDocument(t)){
                        System.out.println("[ALERT] This tweet is already in database.");
                    }else{
                        BasicDBObject tweetContent = new BasicDBObject();
                        tweetContent.put("Search Keyword", this.state.getItem().getSearch_keyword());
                        tweetContent.put("Origin Keyword", this.state.getItem().getOrigin_keyword());
                        tweetContent.put("Item Type", this.state.getItem().getItem_type());
                        tweetContent.put("Category", this.state.getItem().getHigh_cat());
                        tweetContent.put("Sub-category", this.state.getItem().getMiddle_cat());
                        tweetContent.put("Username", t.getUsername());
                        tweetContent.put("TwitterID", t.getId());
                        tweetContent.put("URL", t.getPermalink());
                        tweetContent.put("Date", t.getDate());
                        tweetContent.put("Content", t.getText());
                        tweetContent.put("Number of Comments", t.getComments());
                        tweetContent.put("Number of Favorites", t.getFavorites());
                        tweetContent.put("Number of Retweets", t.getRetweets());
                        tweetContent.put("Hashtag", t.getHashtags());
                        tweetContent.put("Mention", t.getMentions());
                        tweetContent.put("Geo location", t.getGeo());

                        insertDatabase(tweetContent);
                        System.out.println("[INFO Success.");
                    }
                }
                localStart = localStart.plusDays(1);
            }catch (Exception e) {
                System.out.println("[ALERT] Parsing Error occured.. 30sec break.");
                try {
                    e.printStackTrace();
                    Thread.sleep(this.state.getBlockedRest());
                } catch (InterruptedException ie) {
                    System.out.println("[ERROR Blocked Rest Error.");
                    ie.printStackTrace();
                    System.out.println();
                }
            }
        }
        this.DB.getCnn().close();
    }

    public HashMap<String, ArrayList<String>> constructHashIndex(int year){
        HashMap<String, ArrayList<String>> indexing = new HashMap<>();

        this.indexingDT = String.valueOf(year);
        System.out.println("# Current Indexing (year): " + this.indexingDT);

        BasicDBObject andQuery = new BasicDBObject();
        List<BasicDBObject> query = new ArrayList<>();
        query.add(new BasicDBObject("Origin Keyword", this.state.getItem().getOrigin_keyword()));
        query.add(new BasicDBObject("Date", new BasicDBObject("$regex", this.indexingDT)));
        andQuery.put("$and", query);

        DBCursor cursor = this.DB.getCollection().find(andQuery);

        while(cursor.hasNext()){
            DBObject obj = cursor.next();
            String username = obj.get("Username").toString();
            String twitterID = obj.get("TwitterID").toString();

            if(indexing.containsKey(username)){
                indexing.get(username).add(twitterID);
            }else{
                ArrayList<String> arr = new ArrayList<>();
                arr.add(twitterID);
                indexing.put(username, arr);
            }
        }
        cursor.close();
        return indexing;
    }
    private Boolean hasDatabaseDocument(Tweet t){
        Boolean existFlag = false;
        String username = t.getUsername();
        String twitterID = t.getId();
        if(this.indexing.containsKey(username)){
            existFlag = this.indexing.get(username).contains(twitterID);
        }
        return existFlag;
    }
    private void insertDatabase(BasicDBObject tweetContent){
        this.DB.getCollection().insert(tweetContent);
        updateHashIndex(tweetContent);
    }
    private void updateHashIndex(BasicDBObject tweetContent){
        String username = tweetContent.get("Username").toString();
        String twitterID = tweetContent.get("TwitterID").toString();
        if(this.indexing.containsKey(username)){
            this.indexing.get(username).add(twitterID);
        }else{
            ArrayList<String> arr = new ArrayList<>();
            arr.add(twitterID);
            this.indexing.put(username, arr);
        }
    }
}

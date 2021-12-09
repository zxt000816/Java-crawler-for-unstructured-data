package crawlerConfig;

import com.mongodb.*;

import java.util.Arrays;

/**
 * Created by DALAB1 on 2018-04-23.
 */
public class crawlerDAO {
    private final String HOST = "113.198.137.147";
    //private final String HOST = "localhost";
    private final int PORT = 27017;
    private final String DATABASE = "admin";
    private String COLLECTION;

    private final String USERNAME = "root";
    private final String PASSWORD = "gac81-344";

    private MongoClient cnn;
    private DB db;
    private DBCollection col;

    public crawlerDAO(){
        this.cnn = new MongoClient(new ServerAddress(this.HOST, this.PORT), Arrays.asList(MongoCredential.createCredential(this.USERNAME, this.DATABASE, this.PASSWORD.toCharArray())));
        //this.cnn = new MongoClient(new ServerAddress(this.HOST, this.PORT));
        this.db = this.cnn.getDB(this.DATABASE);
    }

    public void setCollection(String COLLECTION){
        this.COLLECTION = COLLECTION;
        this.col = this.db.getCollection(COLLECTION);
    }

    public DBCollection getCollection(){
        return this.col;
    }
    public DB getDb(){
        return this.db;
    }
    public String getCollectionName(){
        return this.COLLECTION;
    }
    public MongoClient getCnn(){
        return this.cnn;
    }
}

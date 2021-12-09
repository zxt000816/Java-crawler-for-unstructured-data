package crawlerConfig;

/**
 * Created by DALAB1 on 2018-04-23.
 */
public class itemConfig {
    private String item_type;
    private String high_cat;
    private String middle_cat;
    private String search_keyword;
    private String origin_keyword;

    public itemConfig(String item_type, String high_cat, String middle_cat, String search_keyword, String origin_keyword){
        this.item_type = item_type;
        this.high_cat = high_cat;
        this.middle_cat = middle_cat;
        this.search_keyword = search_keyword;
        this.origin_keyword = origin_keyword;
    }

    public String getItem_type(){
        return this.item_type;
    }
    public String getHigh_cat(){
        return this.high_cat;
    }
    public String getMiddle_cat(){
        return this.middle_cat;
    }
    public String getSearch_keyword(){
        return this.search_keyword;
    }
    public String getOrigin_keyword(){
        return this.origin_keyword;
    }
}

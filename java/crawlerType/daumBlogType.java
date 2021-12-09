package crawlerType;

import com.gargoylesoftware.htmlunit.BrowserVersion;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.HtmlAnchor;
import com.gargoylesoftware.htmlunit.html.HtmlElement;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.HtmlSpan;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import crawlerConfig.crawlerDAO;
import crawlerConfig.crawlerState;
import org.apache.commons.logging.LogFactory;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.net.URLEncoder;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;

/**
 * Created by DALAB1 on 2018-04-23.
 */
public class daumBlogType implements generalType {
    private crawlerState state;
    private crawlerDAO DB;
    private HashMap<String, ArrayList<Integer>> indexing;
    private String indexingDT;

    @Override
    public void setState(crawlerState state) {
        this.state = state;
    }

    @Override
    public void run() {
        int startYear = Integer.parseInt(this.state.getStartDate().split("-")[0]);
        this.DB = new crawlerDAO();
        this.DB.setCollection(startYear + "daumBlog");
        this.indexing = constructHashIndex(startYear);

        LocalDate localStart = LocalDate.parse(this.state.getStartDate());
        LocalDate localEnd = LocalDate.parse(this.state.getEndDate()).plusDays(1);

        while(localStart.isBefore(localEnd)) {
            if(!this.indexingDT.equals(localStart.toString().split("-")[0])){
                startYear = Integer.parseInt(localStart.toString().split("-")[0]);
                this.DB.setCollection(startYear + "daumBlog");
                this.indexing.clear();
                this.indexing = constructHashIndex(Integer.parseInt(localStart.toString().split("-")[0]));
            }

            try {
                String startDT = localStart.format(DateTimeFormatter.ISO_DATE).replaceAll("-", "");

                String url = "http://search.daum.net/search?q="
                        + URLEncoder.encode(this.state.getItem().getSearch_keyword(), "UTF-8")
                        + "&w=blog&f=section&lpp=10&nil_search=btn&sd="
                        + startDT + "082500&ed=" + startDT + "235959&period=u&enc=utf8DA=STC&SA=daumsec&page=1";

                this.state.setTotalPage((int)(Math.ceil((double)getDocCount(url) / 10.0)));
                System.out.println("[INFO] Total Page (" + this.state.getStartDate() + "): " + this.state.getTotalPage() + ", " + url);

                if(this.state.getTotalPage() > 0){
                    this.state.setCurrPage(this.state.getCurrPage() + 1);
                    for(; this.state.getCurrPage() <= this.state.getTotalPage(); ){
                        System.out.println("[INFO] Date: " + this.state.getStartDate() + ", CurrentPage: " + this.state.getCurrPage() + ", Search Keyword: " + this.state.getItem().getSearch_keyword());
                        HtmlPage page = getPageByHtmlUnitNoJavaScript(url + String.valueOf(this.state.getCurrPage()));
                        Document doc = Jsoup.parse(page.asXml());

                        Elements blocks = doc.select("li[id^=br]");
                        for(Element block : blocks){
                            Elements imgCnt = block.select("span.num_count");
                            int imgCount = 0;
                            if(imgCnt.size() > 0){
                                imgCount = getInteger(imgCnt.first().text());
                            }

                            String blogURL = block.select("a.f_url").first().attr("href");

                            if(!blogURL.contains("blog.daum")){
                                System.out.println("[ALERT] This link is not DaumBlog.");
                            }else{
                                blogURL = getMobileURL(blogURL);
                                if(hasDatabaseDocument(blogURL)){
                                    System.out.println("[ALERT] This link is already in database.");
                                }else{
                                    this.state.setCurrCount(this.state.getCurrCount() + 1);
                                    System.out.println("[INFO] Start collecting document ("
                                            + this.state.getItem().getSearch_keyword()
                                            + ", " + this.state.getStartDate() + ", "
                                            + this.state.getCurrCount() + "):" + blogURL);

                                    parseWebPage(blogURL, imgCount);
                                }
                                if(this.state.getCurrCount() > this.state.getBreakCount()){
                                    System.out.println("[INFO] Take a rest.. 20sec break.");
                                    this.state.setCurrCount(0);
                                    Thread.sleep(this.state.getBreakRest());
                                }
                            }
                            Thread.sleep(500);
                        }
                        this.state.setCurrPage(this.state.getCurrPage() + 1);
                    }
                }
                localStart = localStart.plusDays(1);
                this.state.setStartDate(localStart.toString());
                this.state.setTotalPage(0);
                this.state.setCurrPage(0);
            }catch (Exception e){
                System.out.println("[ALERT] Parsing Error occured.. 30sec break.");
                try{
                    e.printStackTrace();
                    Thread.sleep(this.state.getBlockedRest());
                }catch(InterruptedException ie){
                    System.out.println("[ERROR] Blocked Rest Error.");
                    ie.printStackTrace();
                    System.out.println();
                }
            }
        }
        this.DB.getCnn().close();
    }

    private HashMap<String, ArrayList<Integer>> constructHashIndex(int year){
        HashMap<String, ArrayList<Integer>> indexing = new HashMap<>();

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
            String URL = obj.get("URL").toString().split("http://")[1];
            String[] splitURL = URL.split("/");

            if(indexing.containsKey(splitURL[1])){
                indexing.get(splitURL[1]).add(Integer.parseInt(splitURL[2]));
            }else{
                ArrayList<Integer> arr = new ArrayList<>();
                arr.add(Integer.parseInt(splitURL[2]));
                indexing.put(splitURL[1], arr);
            }
        }

        return indexing;
    }
    private int getDocCount(String url) throws Exception {
        int docCount = 0;
        HtmlPage page = getPageByHtmlUnitNoJavaScript(url);
        Document document = Jsoup.parse(page.asXml());
        String searchCount = document.select("span.txt_info").text();

        if(!searchCount.equals("")){
            searchCount = searchCount.split("/")[1];
            searchCount = searchCount.replaceAll("[^0-9]", "");

            docCount = Integer.parseInt(searchCount);
        }
        return docCount;
    }
    private HtmlPage getPageByHtmlUnit(String url) throws IOException {
        LogFactory.getFactory().setAttribute("org.apache.commons.logging.Log", "org.apache.commons.logging.impl.NoOpLog");
        java.util.logging.Logger.getLogger("com.gargoylesoftware.htmlunit").setLevel(Level.OFF);
        java.util.logging.Logger.getLogger("org.apache.commons.httpclient").setLevel(Level.OFF);

        WebClient webClient = new WebClient(BrowserVersion.CHROME);
        webClient.setJavaScriptTimeout(20000);
        webClient.getOptions().setTimeout(10000);
        webClient.getOptions().setThrowExceptionOnScriptError(false);
        webClient.getOptions().setCssEnabled(false);
        webClient.getOptions().setDownloadImages(false);
        webClient.getOptions().setThrowExceptionOnFailingStatusCode(false);

        final HtmlPage page = webClient.getPage(url);

        return page;
    }
    private HtmlPage getPageByHtmlUnitNoJavaScript(String url) throws IOException {
        LogFactory.getFactory().setAttribute("org.apache.commons.logging.Log", "org.apache.commons.logging.impl.NoOpLog");
        java.util.logging.Logger.getLogger("com.gargoylesoftware.htmlunit").setLevel(Level.OFF);
        java.util.logging.Logger.getLogger("org.apache.commons.httpclient").setLevel(Level.OFF);

        WebClient webClient = new WebClient(BrowserVersion.CHROME);
        webClient.getOptions().setThrowExceptionOnScriptError(false);
        webClient.getOptions().setCssEnabled(false);
        webClient.getOptions().setDownloadImages(false);
        webClient.getOptions().setThrowExceptionOnFailingStatusCode(false);
        webClient.getOptions().setJavaScriptEnabled(false);
        webClient.getOptions().setTimeout(10000);

        final HtmlPage page = webClient.getPage(url);

        return page;
    }
    private int getInteger(String field){
        int number = Integer.valueOf(field.replaceAll("[^0-9]", ""));
        return number;
    }
    private String getMobileURL(String blogURL){
        blogURL = blogURL.split("http://")[1];
        blogURL = "http://m." + blogURL;

        return blogURL;
    }
    private boolean hasDatabaseDocument(String blogURL){
        Boolean existFlag = false;

        String pureURL = blogURL.split("http://")[1];
        String[] splitURL = pureURL.split("/");

        if(this.indexing.containsKey(splitURL[1])){
            existFlag = this.indexing.get(splitURL[1]).contains(Integer.parseInt(splitURL[2]));
        }

        return existFlag;
    }
    private void parseWebPage(String blogURL, int imgCount) throws IOException{
        final HtmlPage page = getPageByHtmlUnitNoJavaScript(blogURL);

        BasicDBObject blogContent = parseBlogContent(page, blogURL);
        
        

        if(blogContent.get("Title").toString().contains(this.state.getItem().getSearch_keyword()) 
        		|| blogContent.get("Content").toString().split(this.state.getItem().getSearch_keyword()).length > 2 
        		|| (blogContent.get("Content").toString().contains(this.state.getItem().getSearch_keyword()) && blogContent.get("Content").toString().contains("가격"))) {

            /* Get NumberOfLikes */
//            parseFavoritesCount(blogURL, blogContent);

            /* Get NumberOfImages */
            blogContent.put("Number of Images", imgCount);
            
            

            //see if it has div element in the first place
            List<HtmlAnchor> commentButton = (List) page.getByXPath("//span[@class='count_comment']");
                       
//            if (commentButton.size() > 0) {
//                commentButton = (List) page.getByXPath("//div[@class='use_btns']//a[@class='btn_cmt test_0']");
//                if (commentButton.size() > 0) {
//                    HtmlPage commentPage = commentButton.get(0).click();
//
//                    BasicDBList blogCommentList = parseBlogComment(commentPage, blogURL);
//
//                    blogContent.put("Number of Comments", blogCommentList.size());
//                    blogContent.put("Comments", blogCommentList);
//                    System.out.println("Number of Comments: " + blogContent.get("Number of Comments").toString());
//                }
//            }
            insertDatabase(blogContent);
            System.out.println("[INFO] Success.");
        } else {
            System.out.println("[INFO] Irrelevant Document.");
        }
    }
    private BasicDBObject parseBlogContent(HtmlPage page, String blogURL) throws IOException{
        BasicDBObject blogContent = new BasicDBObject();
        Document doc = Jsoup.parse(page.asXml());

        blogContent.put("Search Keyword", this.state.getItem().getSearch_keyword());
        blogContent.put("Origin Keyword", this.state.getItem().getOrigin_keyword());
        blogContent.put("Item Type", this.state.getItem().getItem_type());
        blogContent.put("Category", this.state.getItem().getHigh_cat());
        blogContent.put("Sub-category", this.state.getItem().getMiddle_cat());
        blogContent.put("URL", blogURL);

        /* Get Title */
        Elements title = doc.select("h2.tit_blogview");
        if(title.size() > 0){
            blogContent.put("Title", title.first().text());
        }else{
            blogContent.put("Title", "");
        }

        /* Get Date */
        Elements date = doc.select("time.txt_date");
        if(date.size() > 0){
            blogContent.put("Date", date.first().text());
        }else{
            blogContent.put("Date", "");
        }

        /* Get Contents */
        Elements content = doc.select("div.blogview_content");
        if(content.size() > 0){
            content.select("div.use_btns").remove();
            content.select("div.relation_article").remove();
            
            content.select("div.list_tag").remove();
            content.select("div.container_postbtn").remove();
            
            blogContent.put("Content", content.first().text());
        }else{
            blogContent.put("Content", "");
        }

        /* Initialization */
        blogContent.put("Number of Likes", -1);
        
        
        Elements numOfComments = doc.select("div.blogview_info span.count_comment");
        if (numOfComments.size() > 0) {
        	blogContent.put("Number of Comments", Integer.parseInt(numOfComments.first().text()));
        } else {
        	blogContent.put("Number of Comments", 0);
        }

        return blogContent;
    }
    private void parseFavoritesCount(String blogURL, BasicDBObject blogContent) throws IOException{
        HtmlPage page = getPageByHtmlUnitNoJavaScript(blogURL);
        List<HtmlSpan> spanList = (List)page.getByXPath("//span[@class='num_empathy uoc-count']");

        if(spanList.size() > 0) {
            System.out.println("Favorites button? 'existed.'");
            page = getPageByHtmlUnit(blogURL);
            spanList = (List)page.getByXPath("//span[@class='num_empathy uoc-count']");
            String numberOfempathy = spanList.get(0).asText().replaceAll("[^0-9]", "");
            System.out.println("Number of empathy: " + numberOfempathy);
            if (!numberOfempathy.equals("")) {
                blogContent.put("Number of Likes", numberOfempathy);
            }
        }else{
            System.out.println("Favorites button? 'not existed.'");
        }
        System.out.println("Number of Likes: " + blogContent.get("Number of Likes").toString());
    }
    private BasicDBList parseBlogComment(HtmlPage page, String blogURL) throws IOException{
        List<HtmlElement> authors = (List) page.getByXPath("//li[not(@class='reply')]//p[@class='writer']") ;
        List<HtmlElement> texts = (List) page.getByXPath("//li[not(@class='reply')]//p[@class='text']") ;
        List<HtmlElement> dates = (List) page.getByXPath("//li[not(@class='reply')]//p[@class='date']") ;

        //comments object
        BasicDBList comments = new BasicDBList();
        for(int i = 0; i < authors.size(); i++) {
            BasicDBObject newsComments = new BasicDBObject();
            if(!authors.get(i).asText().contains("주인과 글쓴이만")) {
                newsComments.put("Comment Author", authors.get(i).asText().replaceAll("답글", ""));
            }else {
                continue;
            }
            comments.add(newsComments);
        }

        for (int i = 0; i < comments.size(); i++) {
            ((BasicDBObject)comments.get(i)).put("Comment Body",texts.get(i).asText());
            ((BasicDBObject)comments.get(i)).put("Comment Date",dates.get(i).asText());
        }

        return comments;
    }
    private void insertDatabase(BasicDBObject blogContent){
        this.DB.getCollection().insert(blogContent);
        updateHashIndex(blogContent);
    }
    private void updateHashIndex(BasicDBObject blogContent){
        String blogURL = blogContent.get("URL").toString().split("http://")[1];
        String[] splitURL = blogURL.split("/");

        if(this.indexing.containsKey(splitURL[1])){
            this.indexing.get(splitURL[1]).add(Integer.parseInt(splitURL[2]));
        }else{
            ArrayList<Integer> arr = new ArrayList<>();
            arr.add(Integer.parseInt(splitURL[2]));
            this.indexing.put(splitURL[1], arr);
        }
    }
}

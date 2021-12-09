package crawlerType;

import com.gargoylesoftware.htmlunit.BrowserVersion;
import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.*;
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
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;

/**
 * Created by DALAB1 on 2018-04-23.
 */
public class naverBlogType implements generalType {
    private crawlerState state;
    private crawlerDAO DB;
    private HashMap<String, ArrayList<String>> indexing;
    private String indexingDT;

    @Override
    public void setState(crawlerState state) {
        this.state = state;
    }

    @Override
    public void run(){
        int startYear = Integer.parseInt(this.state.getStartDate().split("-")[0]);
        this.DB = new crawlerDAO();
        this.DB.setCollection(startYear + "naverBlog");
        this.indexing = constructHashIndex(startYear);

        LocalDate localStart = LocalDate.parse(this.state.getStartDate());
        LocalDate localEnd = LocalDate.parse(this.state.getEndDate()).plusDays(1);

        while(localStart.isBefore(localEnd)){
            if(!this.indexingDT.equals(localStart.toString().split("-")[0])){
                startYear = Integer.parseInt(localStart.toString().split("-")[0]);
                this.DB.setCollection(startYear + "naverBlog");
                this.indexing.clear();
                this.indexing = constructHashIndex(startYear);
            }

            try{
                String startDT = localStart.format(DateTimeFormatter.ISO_DATE).replaceAll("-", "");
                System.out.println("[INFO] Current Date: " + localStart.toString());
		
                String url = "https://search.naver.com/search.naver?where=post&query="
                        + URLEncoder.encode(this.state.getItem().getSearch_keyword(), "UTF-8")
                        + "&st=sim&sm=tab_opt&date_from=" + startDT + "&date_to=" + startDT
                        + "&date_option=8&srchby=all&dup_remove=1&qvt=0&start=";
		
                this.state.setTotalPage((int)(Math.ceil((double)getDocCount(url) / 10.0)));
                System.out.println("[INFO] Total Page (" + this.state.getStartDate() + "): " + this.state.getTotalPage());

                if(this.state.getTotalPage() > 0){
                    for(; this.state.getCurrPage() < this.state.getTotalPage();){
                        System.out.println("[INFO] Date: " + this.state.getStartDate() + ", CurrentPage: " + this.state.getCurrPage() + ", Search Keyword: " + this.state.getItem().getSearch_keyword());
                        HtmlPage page = getPageByHtmlUnit(url + String.valueOf(this.state.getCurrPage()) + "1");
                        Document doc = Jsoup.parse(page.asXml());

                        Elements blocks = doc.select("li.sh_blog_top");
                        for(Element block : blocks){
                            String blogURL = block.select("a.url").first().text();
                            if(!blogURL.contains("blog.naver.com")){
                                System.out.println("[ALERT] This link is not NaverBlog.");
                            }else{
                                System.out.println("[INFO] Current BlogURL: " + blogURL);
                                if(hasDatabaseDocument(blogURL)){
                                    System.out.println("[ALERT] This link is already in database.");
                                }else{
                                    this.state.setCurrCount(this.state.getCurrCount() + 1);
                                    blogURL = "https://m." + blogURL;
                                    System.out.println("[INFO] Start collecting document ("
                                            + this.state.getItem().getSearch_keyword()
                                            + ", " + this.state.getStartDate() + ", "
                                            + this.state.getCurrCount() + "):" + blogURL);

                                    if(!blogURL.contains("tresmingnon")) {
                                        try {
                                            parseWebPage(blogURL);
                                        } catch (FailingHttpStatusCodeException fe) {
                                            System.out.println("[ERROR] getURL Error occured..");
                                            System.out.println(fe.getMessage());
                                            continue;
                                        }catch(IllegalArgumentException iae){
                                            System.out.println("[ERROR] Parsing Error occured..");
                                            System.out.println(iae.getMessage());
                                            continue;
                                        }
                                    }
                                }

                                if(this.state.getCurrCount() > this.state.getBreakCount()) {
                                    System.out.println("[INFO] Take a rest.. 20sec break.");
                                    this.state.setCurrCount(0);
                                    Thread.sleep(this.state.getBreakRest());
                                }
                            }
                            Thread.sleep(1000);
                        }
                        this.state.setCurrPage(this.state.getCurrPage() + 1);
                    }
                }
                localStart = localStart.plusDays(1);
                this.state.setStartDate(localStart.toString());
                this.state.setTotalPage(0);
                this.state.setCurrPage(0);
            }catch(Exception e){
                System.out.println("[ERROR] Parsing Error occured.. 30sec break.");
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

    public HashMap<String, ArrayList<String>> constructHashIndex(int year){
        HashMap<String, ArrayList<String>> indexing = new HashMap<>();

        this.indexingDT =  String.valueOf(year);
        System.out.println("# Current Indexing (year): " + this.indexingDT);

        BasicDBObject andQuery = new BasicDBObject();
        List<BasicDBObject> query = new ArrayList<>();
        query.add(new BasicDBObject("Origin Keyword", this.state.getItem().getOrigin_keyword()));
        query.add(new BasicDBObject("Date", new BasicDBObject("$regex", this.indexingDT)));
        andQuery.put("$and", query);

        DBCursor cursor = this.DB.getCollection().find(andQuery);

        while(cursor.hasNext()){
            DBObject obj = cursor.next();
            String URL;
            String[] splitURL;
            String blogID = "";
            String logNo = "";
            if(obj.get("URL").toString().contains("https")){
                URL = obj.get("URL").toString().split("https://")[1];
                splitURL = URL.split("/");
                blogID = splitURL[1];
                logNo = splitURL[2];
            }else{
                URL = obj.get("URL").toString().split("blogId=")[1];
                splitURL = URL.split("&");
                blogID = splitURL[0];
                logNo = splitURL[1].split("=")[1];
            }

            if(!blogID.equals("") && !logNo.equals("")) {
                if (indexing.containsKey(blogID)) {
                    indexing.get(blogID).add(logNo);
                } else {
                    ArrayList<String> arr = new ArrayList<>();
                    arr.add(logNo);
                    indexing.put(blogID, arr);
                }
            }
        }
        cursor.close();
        return indexing;
    }
    private int getDocCount(String url) throws Exception{
        int docCount = 0;
        HtmlPage page = getPageByHtmlUnit(url);
        Document document = Jsoup.parse(page.asXml());
        String searchCount = document.select("span.title_num").text();

        if(!searchCount.equals("")){
            searchCount = searchCount.split("/")[1];
            searchCount = searchCount.replaceAll("[^0-9]", "");

            docCount = Integer.valueOf(searchCount);
            if(docCount > 1000){
                docCount = 1000;
            }
        }
        return docCount;
    }
    private HtmlPage getPageByHtmlUnit(String url) throws FailingHttpStatusCodeException, IOException {
        LogFactory.getFactory().setAttribute("org.apache.commons.logging.Log", "org.apache.commons.logging.impl.NoOpLog");
        java.util.logging.Logger.getLogger("com.gargoylesoftware.htmlunit").setLevel(Level.OFF);
        java.util.logging.Logger.getLogger("org.apache.commons.httpclient").setLevel(Level.OFF);

        WebClient webClient = new WebClient(BrowserVersion.CHROME);
        webClient.getOptions().setTimeout(10000);
        webClient.getOptions().setThrowExceptionOnScriptError(false);
        webClient.getOptions().setCssEnabled(false);
        webClient.getOptions().setDownloadImages(false);
        webClient.getOptions().setJavaScriptEnabled(false);
        webClient.getOptions().setThrowExceptionOnFailingStatusCode(false);

        final HtmlPage page = webClient.getPage(url);

        return page;
    }
    private HtmlPage getPageByHtmlJavascript(String url) throws FailingHttpStatusCodeException, IOException{
        LogFactory.getFactory().setAttribute("org.apache.commons.logging.Log", "org.apache.commons.logging.impl.NoOpLog");
        java.util.logging.Logger.getLogger("com.gargoylesoftware.htmlunit").setLevel(Level.OFF);
        java.util.logging.Logger.getLogger("org.apache.commons.httpclient").setLevel(Level.OFF);

        WebClient webClient = new WebClient(BrowserVersion.CHROME);
        webClient.getOptions().setThrowExceptionOnScriptError(false);
        webClient.getOptions().setCssEnabled(false);
        webClient.getOptions().setDownloadImages(false);
        webClient.getOptions().setThrowExceptionOnFailingStatusCode(false);
        webClient.getOptions().setTimeout(10000);
        webClient.setJavaScriptTimeout(20000);

        final HtmlPage page = webClient.getPage(url);

        return page;
    }
    private Boolean hasDatabaseDocument(String blogURL){
        Boolean existFlag = false;

        String[] splitURL = blogURL.split("/");
        if(splitURL.length > 2) {
            String blogID = splitURL[1];
            String logNo = splitURL[2];

            if (this.indexing.containsKey(blogID)) {
                existFlag = this.indexing.get(blogID).contains(logNo);
            }
        }
        return existFlag;
    }
    private void parseWebPage(String blogURL) throws FailingHttpStatusCodeException, IOException{
        final HtmlPage page = getPageByHtmlUnit(blogURL);

        BasicDBObject blogContent = parseBlogContent(page, blogURL);
        if(blogContent.get("Title").toString().contains(this.state.getItem().getSearch_keyword()) || blogContent.get("Content").toString().split(this.state.getItem().getSearch_keyword()).length > 2 || (blogContent.get("Content").toString().contains(this.state.getItem().getSearch_keyword()) && blogContent.get("Content").toString().contains("가격"))) {
            blogURL = convertURL(blogURL);
            parseBlogRest(blogURL, blogContent);
            System.out.println("Title: " + blogContent.get("Title").toString());
            System.out.println("Date: " + blogContent.get("Date").toString());
            System.out.println("Content: " + blogContent.get("Content").toString());
            System.out.println("Number of Likes: " + blogContent.get("Number of Likes").toString());
            System.out.println("Number of Images: " + blogContent.get("Number of Images").toString());
            System.out.println("Number of Comments: " + blogContent.get("Number of Comments").toString());
            insertDatabase(blogContent);
            System.out.println("[INFO] Success.");
        }else{
            System.out.println("[INFO] Irrelevant Document.");
        }
    }
    private BasicDBObject parseBlogContent(HtmlPage page, String blogURL) throws FailingHttpStatusCodeException, IOException{
        BasicDBObject blogContent = new BasicDBObject();
        Document doc = Jsoup.parse(page.asXml());

        blogContent.put("Search Keyword", this.state.getItem().getSearch_keyword());
        blogContent.put("Origin Keyword", this.state.getItem().getOrigin_keyword());
        blogContent.put("Item Type", this.state.getItem().getItem_type());
        blogContent.put("Category", this.state.getItem().getHigh_cat());
        blogContent.put("Sub-category", this.state.getItem().getMiddle_cat());
        blogContent.put("URL", blogURL);

        /* Get Title */
        Elements title = doc.select("div.tit_area");
        if(title.size() > 0){
            blogContent.put("Title", title.first().text());
        }else{
            title = doc.select("div.se_textView h3.se_textarea");
            if(title.size() > 0){
                System.out.println("Title: " + title.first().text());
                blogContent.put("Title", title.first().text());
            }else {
                blogContent.put("Title", "");
            }
        }

        /* Get Date */
        Elements date = doc.select("p.se_date");
        if(date.size() > 0){
            String inputDT = date.first().text();
            if(inputDT.matches(".*[ㄱ-ㅎㅏ-ㅣ가-힣]+.*")) {
                inputDT = getConvertedDate(inputDT);
            }
            blogContent.put("Date", inputDT);
        }else{
            date = doc.select("p.blog_date");
            if(date.size() > 0){
                String inputDT = date.first().text();
                if(inputDT.matches(".*[ㄱ-ㅎㅏ-ㅣ가-힣]+.*")) {
                    inputDT = getConvertedDate(inputDT);
                }
                blogContent.put("Date", inputDT);
            }else {
                blogContent.put("Date", "");
            }
        }

        /* Get Contents */
        Elements content = doc.select("div.se_component_wrap.sect_dsc.__se_component_area");
        if(content.size() > 0){
            blogContent.put("Content", content.text());
        }else {
            content = doc.select("div#viewTypeSelector");
            if(content.size() > 0){
                blogContent.put("Content", content.text());
            }else {
                blogContent.put("Content", "");
            }
        }

        /* Initialize Number of Likes */
        blogContent.put("Number of Likes", -1);

        /* Get NumberOfImages */
        Elements numberOfImages = doc.select("div.se_component_wrap.sect_dsc.__se_component_area img");
        int number = 0;
        if(numberOfImages.size() > 0) {
            for (Element image : numberOfImages) {
                if (!image.attr("src").contains("emoticon") && !image.attr("src").contains("static.naver.net"))
                    number++;
            }
        }else{
            numberOfImages = doc.select("div#viewTypeSelector img");
            for (Element image : numberOfImages) {
                if (!image.attr("src").contains("emoticon") && !image.attr("src").contains("static.naver.net"))
                    number++;
            }
        }
        blogContent.put("Number of Images", number);

        /* Get Comments */
        blogContent.put("Number of Comments", 0);
        Elements comments = doc.select("a.btn_reply>em");
        if(comments.size() > 0){
            blogContent.put("Number of Comments", getInteger(comments.first().text()));
        }

        return blogContent;
    }
    private void parseBlogRest(String blogURL, BasicDBObject blogContent) throws FailingHttpStatusCodeException, IOException, IllegalArgumentException {
        //List<HtmlElement> like_more = (List)page.getByXPath("//a[@class='btn_like_more']");
        /* Get NumberOfLikes */
        HtmlPage javaPage = getPageByHtmlJavascript(blogURL);
        Document doc = Jsoup.parse(javaPage.asXml());
        Elements likes = doc.select("em.u_cnt");
        if(likes.size() > 0) {
            if(likes.first().text().equals("")){
                blogContent.put("Number of Likes", 0);
            } else {
                blogContent.put("Number of Likes", getInteger(likes.first().text()));
            }
        }else{
            blogContent.put("Number of Likes", 0);
        }
        System.out.println("Number of Likes: " + blogContent.get("Number of Likes").toString());
    }
    private int getInteger(String field){
        int number = Integer.valueOf(field.replaceAll("[^0-9]", ""));
        return number;
    }
    private String convertURL(String blogURL){
        String transURL = blogURL.split("https://m\\.")[1];
        String[] parts = transURL.split("/");
        String res = "http://" + parts[0] + "/PostView.nhn?blogId=" + parts[1] + "&logNo=" + parts[2] + "&redirect=Log&widgetTypeCall=false&directAccess=true";
        return res;
    }
    private void insertDatabase(BasicDBObject blogContent){
        this.DB.getCollection().insert(blogContent);
        updateHashIndex(blogContent);
    }
    private void updateHashIndex(BasicDBObject blogContent){
        String URL = blogContent.get("URL").toString().split("https://")[1];
        String[] splitURL = URL.split("/");
        String blogID = splitURL[1];
        String logNo = splitURL[2];

        if(this.indexing.containsKey(blogID)){
            this.indexing.get(blogID).add(logNo);
        }else{
            ArrayList<String> arr = new ArrayList<>();
            arr.add(logNo);
            this.indexing.put(blogID, arr);
        }
    }
    private String getConvertedDate(String inputDT){
        SimpleDateFormat fm = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        Date currDT = new Date();
        long lnDT = currDT.getTime();
        String convertedDT = inputDT;

        if(convertedDT.contains("전")) {
            if (inputDT.contains("시간")) {
                int hour = getInteger(inputDT);
                lnDT = lnDT - (hour * 3600000);
                convertedDT = fm.format(lnDT);
            } else if (inputDT.contains("분")) {
                int min = getInteger(inputDT);
                lnDT = lnDT - (min * 60000);
                convertedDT = fm.format(lnDT);
            } else if (inputDT.contains("어제")) {
                lnDT = lnDT - 86400000;
                convertedDT = fm.format(lnDT);
            } else if (inputDT.contains("일")) {
                int day = getInteger(inputDT);
                lnDT = lnDT - (day * 86400000);
                convertedDT = fm.format(lnDT);
            }
        }else{
            convertedDT = convertedDT.replaceAll(".*[ㄱ-ㅎㅏ-ㅣ가-힣]+.*", "");
        }
        return convertedDT;
    }
}

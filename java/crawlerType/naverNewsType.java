package crawlerType;

import com.gargoylesoftware.htmlunit.BrowserVersion;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.HtmlAnchor;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.mongodb.*;
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
public class naverNewsType implements generalType {
    private crawlerState state;
    private crawlerDAO publishedDB;
    private crawlerDAO broadcastDB;
    private HashMap<Integer, ArrayList<Integer>> indexing_published;
    private HashMap<Integer, ArrayList<Integer>> indexing_broadcast;
    private String indexingDT;

    @Override
    public void setState(crawlerState state) {
        this.state = state;
    }

    @Override
    public void run() {
        int startYear = Integer.parseInt(this.state.getStartDate().split("-")[0]);
        this.publishedDB = new crawlerDAO();
        this.publishedDB.setCollection(startYear + "publishedNews");
        this.broadcastDB = new crawlerDAO();
        this.broadcastDB.setCollection(startYear + "broadcastNews");
        this.indexing_published = constructHashIndex(startYear, 1);
        this.indexing_broadcast = constructHashIndex(startYear, 2);

        LocalDate localStart = LocalDate.parse(this.state.getStartDate());
        LocalDate localEnd = LocalDate.parse(this.state.getEndDate()).plusDays(1);

        while(localStart.isBefore(localEnd)){
            if(!this.indexingDT.equals(localStart.toString().split("-")[0])){
                startYear = Integer.parseInt(localStart.toString().split("-")[0]);
                this.publishedDB.setCollection(startYear + "publishedNews");
                this.broadcastDB.setCollection(startYear + "broadcastNews");
                this.indexing_published.clear();
                this.indexing_broadcast.clear();
                this.indexing_published = constructHashIndex(Integer.parseInt(localStart.toString().split("-")[0]), 1);
                this.indexing_broadcast = constructHashIndex(Integer.parseInt(localStart.toString().split("-")[0]), 2);
            }

            try{
                String startDT = localStart.format(DateTimeFormatter.ISO_DATE).replaceAll("-", "");

                String url = "https://search.naver.com/search.naver?ie=utf8&where=news&query="
                        + URLEncoder.encode(this.state.getItem().getSearch_keyword(), "UTF-8")
                        + "&sm=tab_pge&sort=2&photo=0&field=0&pd=3&ds=" + startDT
                        + "&de=" + startDT + "&qvt=0&start=";

                System.out.println("[INFO] Search URL: " + url);

                this.state.setTotalPage((int)(Math.ceil((double)getDocCount(url) / 10.0)));
                System.out.println("[INFO] Total Page (" + this.state.getStartDate() + "): " + this.state.getTotalPage());

                if(this.state.getTotalPage() > 0){
                    for(; this.state.getCurrPage() < this.state.getTotalPage();){
                        System.out.println("[INFO] Date: " + this.state.getStartDate() + ", CurrentPage: " + this.state.getCurrPage() + ", Search Keyword: " + this.state.getItem().getSearch_keyword());
                        HtmlPage page = getPageByHtmlUnit(url + String.valueOf(this.state.getCurrPage()) + "1");
                        Document doc = Jsoup.parse(page.asXml());

                        Elements blocks = doc.select("ul.type01 li");
                        for(Element block : blocks){
                            String newsURL = block.select("dd.txt_inline a").first().attr("href");
                            if (!newsURL.contains("news.naver") || newsURL.contains("sports")) {
                                System.out.println("[ALERT] This link is not Naver news.");
                            }else{
                                if (hasDatabaseDocument(newsURL)) {
                                    System.out.println("[ALERT] This link is already in database.");
                                }else{
                                    this.state.setCurrCount(this.state.getCurrCount() + 1);
                                    System.out.println("[INFO] Start collecting document ("
                                            + this.state.getItem().getSearch_keyword()
                                            + ", " + this.state.getStartDate() + ", "
                                            + this.state.getCurrCount() + "):" + newsURL);
                                    try {
                                        parseWebPage(newsURL);
                                    }catch(NullPointerException ne){
                                        System.out.println("[ERROR] NullPointerException occured.");
                                        System.out.println(ne.getMessage());
                                        continue;
                                    }
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
            }catch(MongoTimeoutException me){
                System.out.println("[ERROR] MongoTimeoutException occured.");
                System.out.println(me.getMessage());
            }catch(MongoExecutionTimeoutException mee) {
                System.out.println("[ERROR] MongoExecutionTimeoutException occured.");
                System.out.println(mee.getMessage());
            }catch(MongoSocketReadTimeoutException msrte){
                System.out.println("[ERROR] MongoSocketReadTimeoutException occured.");
                System.out.println(msrte.getMessage());
            }catch(MongoSocketReadException re) {
                System.out.println("[ERROR] MongoDB Connection was closed.");
                System.out.println(re.getMessage());
                startYear = Integer.parseInt(localStart.toString().split("-")[0]);
                this.publishedDB = new crawlerDAO();
                this.publishedDB.setCollection(startYear + "publishedNews");
                this.broadcastDB = new crawlerDAO();
                this.broadcastDB.setCollection(startYear + "broadcastNews");
            }catch(Exception e){
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
        this.broadcastDB.getCnn().close();
        this.publishedDB.getCnn().close();
    }

    private HashMap<Integer, ArrayList<Integer>> constructHashIndex(int year, int flag){
        HashMap<Integer, ArrayList<Integer>> indexing = new HashMap<>();

        this.indexingDT = String.valueOf(year);
        System.out.println("# Current Indexing (year): " + this.indexingDT);

        BasicDBObject andQuery = new BasicDBObject();
        List<BasicDBObject> query = new ArrayList<>();
        query.add(new BasicDBObject("Origin Keyword", this.state.getItem().getOrigin_keyword()));
        query.add(new BasicDBObject("Date", new BasicDBObject("$regex", this.indexingDT)));
        andQuery.put("$and", query);

        DBCursor cursor;
        if(flag == 1){
            cursor = this.publishedDB.getCollection().find(andQuery);
        }else{
            cursor = this.broadcastDB.getCollection().find(andQuery);
        }

        while(cursor.hasNext()){
            DBObject obj = cursor.next();
            String URL = obj.get("URL").toString().split("oid=")[1];
            String[] splitURL = URL.split("&");
            int oid = Integer.parseInt(splitURL[0]);
            int aid = Integer.parseInt(splitURL[1].replaceAll("[^0-9]", ""));

            if(indexing.containsKey(oid)){
                indexing.get(oid).add(aid);
            }else{
                ArrayList<Integer> arr = new ArrayList<>();
                arr.add(aid);
                indexing.put(oid, arr);
            }
        }
        cursor.close();

        return indexing;
    }
    private int getDocCount(String url) throws Exception {
        int docCount = 0;
        HtmlPage page = getPageByHtmlUnit(url);
        Document document = Jsoup.parse(page.asXml());
        String searchCount = document.select("a.news_tit").text(); // Yifan: (div.title_desc) -> (a.news_tit)

        if(!searchCount.equals("")){
            searchCount = searchCount.split("/")[1];
            searchCount = searchCount.replaceAll("[^0-9]", "");

            docCount = Integer.parseInt(searchCount);
            System.out.println("[INFO] DOC Count: " + docCount);
        }
        return docCount;
    }
    private HtmlPage getPageByHtmlUnit(String url) throws IOException {
        LogFactory.getFactory().setAttribute("org.apache.commons.logging.Log", "org.apache.commons.logging.impl.NoOpLog");
        java.util.logging.Logger.getLogger("com.gargoylesoftware.htmlunit").setLevel(Level.OFF);
        java.util.logging.Logger.getLogger("org.apache.commons.httpclient").setLevel(Level.OFF);

        WebClient webClient = new WebClient(BrowserVersion.CHROME);
        webClient.getOptions().setThrowExceptionOnScriptError(false);
        webClient.getOptions().setCssEnabled(false);
        webClient.getOptions().setDownloadImages(false);
        webClient.getOptions().setThrowExceptionOnFailingStatusCode(false);

        final HtmlPage page = webClient.getPage(url);

        return page;
    }
    private boolean hasDatabaseDocument(String newsURL){
        Boolean existFlag = false;

        String URL = newsURL.split("oid=")[1];
        String[] splitURL = URL.split("&");
        if(splitURL.length > 1){
            int oid = Integer.parseInt(splitURL[0]);
            int aid = Integer.parseInt(splitURL[1].replaceAll("[^0-9]", ""));

            if(this.indexing_published.containsKey(oid)){
                if(this.indexing_published.get(oid).contains(aid)){
                    existFlag = true;
                }
            }else if(this.indexing_broadcast.containsKey(oid)){
                if(this.indexing_broadcast.get(oid).contains(aid)){
                    existFlag = true;
                }
            }
        }
        return existFlag;
    }
    private void parseWebPage(String newsURL) throws IOException{
        final HtmlPage page = getPageByHtmlUnit(newsURL);

        int sizeError = page.getByXPath("//h1[@class='error_title']").size();
        boolean hasNotFoundPage = (sizeError > 0) ? true : false;

        if(!hasNotFoundPage) {
            BasicDBObject newsContent = parseNewsContent(page, newsURL);

            /* see if it has div with stylesheet display none */
            int size = page.getByXPath("//div[@class='u_cbox_view_comment' and @style='display: none;']").size();
            boolean hasCommentsPage = size > 0 ? false : true;
            /* see if it has div element in the first place */
            size = page.getByXPath("//div[@class='u_cbox_view_comment']").size();
            boolean hasCommentsButton = size > 0 ? true : false;

            if (hasCommentsPage && hasCommentsButton) {
                /* get div which has a 'name' attribute of 'cbox' */
                List<HtmlAnchor> commentsList = page.getByXPath("//a[@class='u_cbox_btn_view_comment']");

                /* proceed to click if there are comments */
                HtmlAnchor commentButton = commentsList.get(0);
                HtmlPage commentPage = commentButton.click();

                int commentLimit = 0;
                while (hasComments(commentPage) && (commentLimit < 10)) {
                    List<HtmlAnchor> moreList = commentPage.getByXPath("//a[@class='u_cbox_btn_more']");

                    try {
                        HtmlAnchor moreButton = moreList.get(0);
                        commentPage = moreButton.click();
                        commentLimit++;
                    } catch (Exception e) {
                        System.out.println("[INFO] ERROR OCCURED: Comments");
                        break;
                    }
                }

                String htmlCommentsPage = commentPage.asXml();
                BasicDBList newsCommentList = parseNewsComments(htmlCommentsPage);

                newsContent.put("Number of Comments", newsCommentList.size());
                newsContent.put("Comments", newsCommentList);
            }
            if(newsContent.get("Title").toString().contains(this.state.getItem().getSearch_keyword()) || newsContent.get("Content").toString().split(this.state.getItem().getSearch_keyword()).length > 2 || (newsContent.get("Content").toString().contains(this.state.getItem().getSearch_keyword()) && newsContent.get("Content").toString().contains("가격"))) {
                insertDatabase(newsContent);
                System.out.println("[INFO] Success.");
            }else{
                System.out.println("[INFO] Irrelevant Document.");
            }
        }
    }
    private BasicDBObject parseNewsContent(HtmlPage page, String newsURL) throws IOException{
        BasicDBObject newsContent = new BasicDBObject();
        Document doc = Jsoup.parse(page.asXml());

        newsContent.put("Search Keyword", this.state.getItem().getSearch_keyword());
        newsContent.put("Origin Keyword", this.state.getItem().getOrigin_keyword());
        newsContent.put("Item Type", this.state.getItem().getItem_type());
        newsContent.put("Category", this.state.getItem().getHigh_cat());
        newsContent.put("Sub-category", this.state.getItem().getMiddle_cat());
        newsContent.put("URL", newsURL);

        /* Get Title */
        Elements title = doc.select("h3#articleTitle");
        if(title.size() > 0){
            newsContent.put("Title", title.first().text());
        }else{
            title = doc.select("h2.end_tit");
            if(title.size() > 0){
                newsContent.put("Title", title.first().text());
            }else {
                newsContent.put("Title", "");
            }
        }

        /* Get Publisher */
        Elements publisher = doc.select("div.press_logo>a>img");
        if(publisher.size() > 0){
            newsContent.put("Publisher", publisher.first().attr("alt"));
        }else{
            newsContent.put("Publisher", "");
        }

        /* Get Original Link */
        Elements originalLink = doc.select("div.sponsor>a.btn_artialoriginal");
        if(originalLink.size() > 0){
            newsContent.put("Original Link", originalLink.first().attr("href"));
        }else{
            originalLink = doc.select("a.btn_news_origin");
            if(originalLink.size() > 0){
                newsContent.put("Original Link", originalLink.first().attr("href"));
            }else {
                newsContent.put("Original Link", "");
            }
        }

        /* Get uploaded Date */
        Elements uploadDT = doc.select("div.sponsor>span.t11");
        if(uploadDT.size() > 0){
            newsContent.put("Date", uploadDT.first().text());
        }else{
            uploadDT = doc.select("span.author>em");
            if(uploadDT.size() > 0){
                newsContent.put("Date", uploadDT.first().text());
            }else{
                newsContent.put("Date", "");
            }
        }

        /* Get numberOfEmotions */
        Elements numberOfEmotions = doc.select("span.u_likeit_text._count.num");
        if(numberOfEmotions.size()>0) {
            newsContent.put("Number of Emotions", getInteger(numberOfEmotions.first().text()));
        }else {
            newsContent.put("Number of Emotions", 0);
        }

        /* Get numberOfComments */
        newsContent.put("Number of Comments", 0);

        /* Get numberOfImages */
        Elements numberOfImages = doc.select("div#articleBodyContents img");
        newsContent.put("Number of Images", numberOfImages.size());

        /* Get Contents */
        Element content = doc.select("div.article_body").first();
        newsContent.put("Content", content.text());

        /* Get Emotions */
        BasicDBObject newsEmotions = getEmotions(doc);
        newsContent.put("Emotions", newsEmotions);

        return newsContent;
    }
    private int getInteger(String field){
        int number = Integer.valueOf(field.replaceAll("[^0-9]", ""));
        return number;
    }
    private BasicDBObject getEmotions(Document document) throws IOException{
        BasicDBObject newsEmotions = new BasicDBObject();

        Elements like = document.select("div.end_btn li.u_likeit_list.good span.u_likeit_list_count._count");
        if(like.size() > 0){
            newsEmotions.put("Number of Likes", getInteger(like.first().text()));
        }else{
            newsEmotions.put("Number of Likes", 0);
        }

        Elements warm = document.select("div.end_btn li.u_likeit_list.warm span.u_likeit_list_count._count");
        if(warm.size() > 0){
            newsEmotions.put("Number of Warms", getInteger(warm.first().text()));
        }else {
            newsEmotions.put("Number of Warms", 0);
        }

        Elements sad = document.select("div.end_btn li.u_likeit_list.sad span.u_likeit_list_count._count");
        if(sad.size() > 0){
            newsEmotions.put("Number of Sads", getInteger(sad.first().text()));
        }else {
            newsEmotions.put("Number of Sads", 0);
        }

        Elements angry = document.select("div.end_btn li.u_likeit_list.angry span.u_likeit_list_count._count");
        if(angry.size() > 0){
            newsEmotions.put("Number of Angries", getInteger(angry.first().text()));
        }else {
            newsEmotions.put("Number of Angries", 0);
        }

        Elements want = document.select("div.end_btn li.u_likeit_list.want span.u_likeit_list_count._count");
        if(want.size() > 0){
            newsEmotions.put("Number of Wants", getInteger(want.first().text()));
        }else {
            newsEmotions.put("Number of Wants", 0);
        }

        return newsEmotions;
    }
    private boolean hasComments(HtmlPage commentPage){
        int size = commentPage.getByXPath("//div[@class='u_cbox_paginate' and @style='display: none;']").size();
        boolean hasComments = size > 0 ? false : true;
        return hasComments;
    }
    private BasicDBList parseNewsComments(String htmlCommentsPage) throws IOException{
        BasicDBList newsCommentList = new BasicDBList();
        Document document = Jsoup.parse(htmlCommentsPage);

        /* Get Comments */
        Elements commentsList = document.select("div.u_cbox_area");
        for(Element comment: commentsList){
            BasicDBObject newsComment = new BasicDBObject();

            Element commentAuthor = comment.select("span.u_cbox_nick").first();
            if(commentAuthor.hasText()){
                newsComment.put("Comment Author", commentAuthor.text());
            }else{
                continue;
            }

            Elements commentBody = comment.select("span.u_cbox_contents");
            if(commentBody.size() > 0){
                newsComment.put("Comment Body", commentBody.first().text());
            }else{
                continue;
            }

            Elements commentDate = comment.select("span.u_cbox_date");
            if(commentDate.size() > 0) {
                newsComment.put("Comment Date", commentDate.first().text());
            }else{

            }

            Elements commentLike = comment.select("em.u_cbox_cnt_recomm");
            if(commentLike.size() > 0){
                newsComment.put("Comment Likes", getInteger(commentLike.first().text()));
            }else{
                newsComment.put("Comment Likes", 0);
            }

            Elements commentDislike = comment.select("em.u_cbox_cnt_unrecomm");
            if(commentDislike.size() > 0){
                newsComment.put("Comment Dislikes", getInteger(commentDislike.first().text()));
            }else{
                newsComment.put("Comment Dislikes", 0);
            }
            newsCommentList.add(newsComment);
        }

        return newsCommentList;
    }
    private void insertDatabase(BasicDBObject newsContent){
        String[] publisherList = {"KBS 뉴스", "MBC 뉴스", "YTN", "JTBC", "SBS 뉴스", "SBS CNBC", "TV조선", "연합뉴스TV", "채널A", "한국경제TV", "뉴스1", "뉴시스", "MBN", "enews24", "KBS 연예", "SBS funE", "OBS TV"};
        ArrayList<String> list = new ArrayList<>();
        for(int i = 0; i < publisherList.length; i++){
            list.add(publisherList[i]);
        }

        if(list.contains(newsContent.get("Publisher"))){
            this.broadcastDB.getCollection().insert(newsContent);
            updateHashIndex(newsContent, 2);
        }else{
            this.publishedDB.getCollection().insert(newsContent);
            updateHashIndex(newsContent, 1);
        }
    }
    private void updateHashIndex(BasicDBObject newsContent, int flag){
        String newsURL = newsContent.get("URL").toString();
        String URL = newsURL.split("oid=")[1];
        String[] splitURL = URL.split("&");
        if(splitURL.length > 1) {
            int oid = Integer.parseInt(splitURL[0]);
            int aid = Integer.parseInt(splitURL[1].replaceAll("[^0-9]", ""));

            if(flag == 1){
                if(this.indexing_published.containsKey(oid)){
                    this.indexing_published.get(oid).add(aid);
                }else{
                    ArrayList<Integer> arr = new ArrayList<>();
                    arr.add(aid);
                    this.indexing_published.put(oid, arr);
                }
            }else{
                if(this.indexing_broadcast.containsKey(oid)){
                    this.indexing_broadcast.get(oid).add(aid);
                }else{
                    ArrayList<Integer> arr = new ArrayList<>();
                    arr.add(aid);
                    this.indexing_broadcast.put(oid, arr);
                }
            }
        }
    }
}

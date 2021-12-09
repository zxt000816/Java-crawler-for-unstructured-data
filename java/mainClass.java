import com.mongodb.BasicDBObject;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import crawlerConfig.crawlerDAO;
import crawlerConfig.crawlerState;
import crawlerConfig.itemConfig;
import crawlerType.*;

import java.text.SimpleDateFormat;
import java.util.*;

import static java.lang.System.exit;

/**
 * Created by DALAB1 on 2018-04-23.
 */
public class mainClass {
    private static String menu_selector;
    private static ArrayList<itemConfig> keywordList;
    private static crawlerState crawler_state;
    private static generalType crawler;

    private static HashMap<String, ArrayList<itemConfig>> itemMap;
    private static String dataType;
    private static boolean option_flag = true;

    public static void main(String[] args){
        if(args.length < 1){
            Scanner scan = new Scanner(System.in);
            menuscript();
            menu_selector = scan.nextLine();
        }else if(args.length > 5){
            menu_selector = args[0];
        }else{
            System.out.println("Please input the correct parameters. (ex) menu1, menu2, menu3, keyword, startDate, endDate.");
            return;
        }

        switch (menu_selector){
            case "1":
                dataType = "News";
                crawler = new naverNewsType();
                break;
            case "2":
                dataType = "NaverBlog";
                crawler = new naverBlogType();
                break;
            case "3":
                dataType = "DaumBlog";
                crawler = new daumBlogType();
                break;
            case "4":
                dataType = "Twitter";
                crawler = new twitterType();
                break;
            default:
                System.out.println("Please select the correct menu.");
                return;
        }

        itemMap = prepareItemList();

        if(args.length < 2){
            Scanner scan = new Scanner(System.in);
            submenuscript_1();
            menu_selector = scan.nextLine();
        }else{
            menu_selector = args[1];
        }

        keywordList = new ArrayList<itemConfig>();
        switch (menu_selector){
            case "1":
                for(Map.Entry<String, ArrayList<itemConfig>> entry : itemMap.entrySet()){
                    ArrayList<itemConfig> itemList = entry.getValue();
                    for(int i = 0; i < itemList.size(); i++){
                        keywordList.add(itemList.get(i));
                    }
                }
                break;
            case "2":
                if(args.length < 3){
                    Scanner scan = new Scanner(System.in);
                    System.out.println("# Input Keyword: ");
                    String temp = scan.nextLine();

                    if(itemMap.containsKey(temp)) {
                        ArrayList<itemConfig> itemList = itemMap.get(temp);
                        for (int i = 0; i < itemList.size(); i++) {
                            keywordList.add(itemList.get(i));
                        }
                    }else{
                        System.out.println("[INFO] A particular item selected.");
                        System.out.println();
                        System.out.println("# Input ItemType (가공식품 or 비가공식품): ");
                        String itemType = scan.nextLine();
                        System.out.println("# Input HighCategory (ex) 축산물, 과채류: ");
                        String highCat = scan.nextLine();
                        System.out.println("# Input MiddleCategory (ex) 버섯, 참외: ");
                        String middleCat = scan.nextLine();
                        System.out.println("# Input Original Keyword (ex) 방울토마토, 계란: ");
                        String originalKeyword = scan.nextLine();

                        keywordList.add(new itemConfig(itemType, highCat, middleCat, temp, originalKeyword));
                    }
                }else{
                    ArrayList<itemConfig> itemList = itemMap.get(args[2]);
                    if(itemList.size() < 0){
                        System.out.println("Please input the correct item.");
                        return;
                    }else{
                        for(int i = 0; i < itemList.size(); i++){
                            keywordList.add(itemList.get(i));
                        }
                    }
                }
                break;
            default:
                System.out.println("Please select the correct menu.");
                return;
        }

        if(args.length < 6){
            Scanner scan = new Scanner(System.in);
            submenuscript_2();
            menu_selector = scan.nextLine();
        }else{
            menu_selector = args[3];
        }

        try {
            switch (menu_selector) {
                case "1":
                    while (option_flag) {
                        for (int i = 0; i < keywordList.size(); i++) {
                            String endDate = getCurrentDate();
                            String startDate = getLatestDate(keywordList.get(i), endDate);
                            startDate = dateFormatter(startDate);

                            if (!startDate.equals("") && !endDate.equals("")) {
                                crawler_state = new crawlerState(keywordList.get(i), dataType, startDate, endDate);
                                crawler.setState(crawler_state);
                                System.out.println("Search Keyword: " + keywordList.get(i).getSearch_keyword());
                                crawler.run();
                            } else {
                                System.out.println("[ERROR] getDate error occured.");
                                return;
                            }
                        }
                        Thread.sleep(3600000);
                    }
                    break;
                case "2":
                    String startDate = "";
                    String endDate = "";
                    if(args.length < 6) {
                        Scanner scan = new Scanner(System.in);
                        System.out.println("# Input startDate (yyyy-MM-dd): ");
                         startDate = scan.nextLine();
                        System.out.println("# Input endDate (yyyy-MM-dd): ");
                         endDate = scan.nextLine();
                    }else{
                        startDate = args[4];
                        endDate = args[5];
                    }

                    for(int i = 0; i < keywordList.size(); i++){
                        crawler_state = new crawlerState(keywordList.get(i), dataType, startDate, endDate);
                        crawler.setState(crawler_state);
                        crawler.run();
                    }

                    break;
                default:
                    System.out.println("Please select the correct menu.");
                    return;
            }
        }catch(InterruptedException e){
            System.out.println("[ERROR] Thread Sleep error occured.");
            e.printStackTrace();
        }finally {
            /* save state */
        }
    }

    public static void menuscript(){
        System.out.println("##############################");
        System.out.println("#         M  E  N  U         #");
        System.out.println("##############################");
        System.out.println("#        1. Naver news       #");
        System.out.println("#        2. Naver blog       #");
        System.out.println("#        3. Daum blog        #");
        System.out.println("#        4. Twitter          #");
        System.out.println("##############################");
        System.out.print("Select: ");
    }

    public static void submenuscript_1(){
        System.out.println("##############################");
        System.out.println("#         M  E  N  U         #");
        System.out.println("##############################");
        System.out.println("#      1. All Keyword        #");
        System.out.println("#      2. Set Keyword        #");
        System.out.println("##############################");
        System.out.print("Select: ");
    }

    public static void submenuscript_2(){
        System.out.println("##############################");
        System.out.println("#         M  E  N  U         #");
        System.out.println("##############################");
        System.out.println("#      1. Latest Update      #");
        System.out.println("#      2. Period Update      #");
        System.out.println("##############################");
        System.out.print("Select: ");
    }

    public static HashMap<String, ArrayList<itemConfig>> prepareItemList(){
        HashMap<String, ArrayList<itemConfig>> itemTable = new HashMap<>();
        String[][] keywords = {
                {"고구마","호박고구마","밤고구마","자색고구마","햇고구마"},
                {"감자","햇감자","깐감자"},
                {"버섯", "머쉬룸", "버석", "버섭", "버슷", "머시룸", "mushroom"},
                {"양송이버섯", "양송이"},
                {"새송이버섯", "새송이","큰느타리버섯"},
                {"팽이버섯","팽나무버섯"},
                {"느타리버섯", "느타리"},
                {"표고버섯", "표고"},
                {"배추", "백채", "숭채", "cabbage"},
                {"김장배추","가을배추","김장용 배추"},
                {"절임배추","절인배추"},
                {"상추","청상추","적상추","양상추","꽃상추"},
                {"무", "나복", "내복", "노복", "청근", "무우", "radish"},
                {"오이","취청오이","가시오이","다다기오이","노각오이"},
                {"딸기", "스트로베리", "strawberry"},
                {"수박", "수과", "서과", "워터멜론", "watermelon"},
                {"참외", "감과", "진과", "첨과", "채미"},
                {"완숙토마토","일반토마토"},
                {"방울토마토", "방토"},
                {"토마토", "도마도", "도마토", "tomato"},
                {"고추", "chili"},
                {"마늘", "garlic"},
                {"깐마늘"},
                {"풋마늘"},
                {"양파", "옥총", "onion", "오니언"},
                {"파프리카", "paprika"},
                {"사과","후지","아오리","홍로","홍옥"},
                {"배","신고배"},
                {"복숭아","황도","백도","천도"},
                {"포도","청포도","캠벨","거봉","머루포도","킹델라웨어","킹데라","델라웨어","세리단","세레단","적포도","레드글러브"},
                {"감귤","귤","진지향","천혜향","한라봉"},
                {"감","단감","대봉","반시","홍시","곶감"},
                {"쇠고기", "소고기", "우육", "황육", "beef", "한우"},
                {"등심","생등심","소등심"},
                {"돼지고기", "돈육", "저육", "제육", "pork"},
                {"삼겹살", "세겹살", "오겹살"},
                {"닭고기", "계육", "치킨", "chicken", "통닭", "일반닭", "육계"},
                {"계란", "달걀", "egg","닭알"},
                {"특란","특계란"},
                {"건고추","말린고추","마른고추"},
                {"김치", "김장김치", "김장", "kimchi", "배추김치"},
                {"구제역", "Foot-and-Mouth Disease", "FMD"},
                {"조류인플루엔자", "고병원성 조류인플루엔자", "HPAI", "가금인플루엔자", "Avian Influenza", "조류독감", "AI"},
                {"아프리카돼지열병", "아프리카 돼지열병", "돼지열병", "Africa swine fever", "ASF"}
        };

        String[][] config = {
                {"신선식품","서류","고구마"},
                {"신선식품","서류","감자"},
                {"신선식품", "특작류", "버섯"},
                {"신선식품", "특작류", "버섯"},
                {"신선식품", "특작류", "버섯"},
                {"신선식품", "특작류", "버섯"},
                {"신선식품", "특작류", "버섯"},
                {"신선식품", "특작류", "버섯"},
                {"신선식품", "엽경채류", "배추"},
                {"신선식품", "엽경채류", "배추"},
                {"신선식품", "엽경채류", "배추"},
                {"신선식품", "엽경채류", "배추"},
                {"신선식품", "근채류", "무"},
                {"신선식품", "과채류", "오이"},
                {"신선식품", "과채류", "딸기"},
                {"신선식품", "과채류", "수박"},
                {"신선식품", "과채류", "참외"},
                {"신선식품", "과채류", "토마토"},
                {"신선식품", "과채류", "토마토"},
                {"신선식품", "과채류", "토마토"},
                {"신선식품", "조미채류", "고추"},
                {"신선식품", "조미채류", "마늘"},
                {"신선식품", "조미채류", "마늘"},
                {"신선식품", "조미채류", "마늘"},
                {"신선식품", "조미채류", "양파"},
                {"신선식품", "조미채류", "파프리카"},
                {"신선식품", "과일류", "사과"},
                {"신선식품", "과일류", "배"},
                {"신선식품", "과일류", "복숭아"},
                {"신선식품", "과일류", "포도"},
                {"신선식품", "과일류", "감귤"},
                {"신선식품", "과일류", "감"},
                {"신선식품", "축산물", "쇠고기_국내산"},
                {"신선식품", "축산물", "쇠고기_국내산"},
                {"신선식품", "축산물", "돼지고기_국내산"},
                {"신선식품", "축산물", "돼지고기_국내산"},
                {"신선식품", "축산물", "닭고기_국내산"},
                {"신선식품", "축산물", "난류"},
                {"신선식품", "축산물", "난류"},
                {"가공식품", "가공식품", "고추가공식품"},
                {"가공식품", "가공식품", "김치"},
                {"가축전염병", "-", "-"},
                {"가축전염병", "-", "-"},
                {"가축전염병", "-", "-"}
        };

        for(int i = 0; i < keywords.length; i++){
            ArrayList<itemConfig> itemList = new ArrayList<>();
            String origin = keywords[i][0];

            //System.out.println("Origin: " + keywords[i][0] + ", Length: " + keywords[i].length);
            for(int j = 0; j < keywords[i].length; j++){
                itemConfig item = new itemConfig(config[i][0], config[i][1], config[i][2], keywords[i][j], origin);
                itemList.add(item);
            }
            itemTable.put(origin, itemList);
        }

        return itemTable;
    }

    public static String getLatestDate(itemConfig item, String currentDate){
        String CurrentDate = currentDate;
        int Year = Integer.parseInt(CurrentDate.split("-")[0]);
        crawlerDAO dao1 = null, dao2 = null;
        boolean flag = true;
        String latestDate = "";

        while(flag) {
            switch (dataType) {
                case "News":
                    dao1 = new crawlerDAO();
                    dao1.setCollection(Year + "publishedNews");
                    dao2 = new crawlerDAO();
                    dao2.setCollection(Year + "broadcastNews");
                    break;
                case "NaverBlog":
                    dao1 = new crawlerDAO();
                    dao1.setCollection(Year + "naverBlog");
                    break;
                case "DaumBlog":
                    dao1 = new crawlerDAO();
                    dao1.setCollection(Year + "daumBlog");
                    break;
                case "Twitter":
                    dao1 = new crawlerDAO();
                    dao1.setCollection(Year + "Twitter");
                    break;
                default:
                    System.out.println("[ERROR] Unknown DataType.");
                    exit(1);
            }

            if(dataType == "News") {
                BasicDBObject query = new BasicDBObject("Search Keyword", item.getSearch_keyword());
                DBCursor cursor1 = dao1.getCollection().find(query);
                DBCursor cursor2 = dao2.getCollection().find(query);

                if(cursor1.count() > 0){
                    cursor1.skip(cursor1.count() - 1);
                }

                if(cursor2.count() > 0){
                    cursor2.skip(cursor2.count() - 1);
                }

                if(cursor1.hasNext()){
                    DBObject obj = cursor1.next();
                    latestDate = obj.get("Date").toString();
                    flag = false;
                }else if(cursor2.hasNext()){
                    DBObject obj = cursor2.next();
                    latestDate = obj.get("Date").toString();
                    flag = false;
                }else{
                    Year--;
                    System.out.println("[INFO] Search Year: " + Year);
                    if(Year < 2010){
                        latestDate = "2010-01-01 00:00";
                        flag = false;
                    }
                }
                cursor1.close();
                cursor2.close();
                dao1.getCnn().close();
                dao2.getCnn().close();
            }else{
                BasicDBObject query = new BasicDBObject("Search Keyword", item.getSearch_keyword());
                DBCursor cursor = dao1.getCollection().find(query);

                if(cursor.count() > 0){
                    cursor.skip(cursor.count() - 1);
                }

                if(cursor.hasNext()){
                    DBObject obj = cursor.next();
                    System.out.println(obj.toString());
                    latestDate = obj.get("Date").toString();
                    flag = false;
                }else{
                    Year--;
                    System.out.println("[INFO] Search Year: " + Year);
                    if(Year < 2010){
                        latestDate = "2010-01-01 00:00";
                        flag = false;
                    }
                }
                cursor.close();
                dao1.getCnn().close();
            }
        }
        System.out.println("[INFO] Latest Date: " + latestDate);
        return latestDate;
    }

    public static String dateFormatter(String original){
        SimpleDateFormat dt_news = new SimpleDateFormat("yyyyMMdd hh:mm");

        SimpleDateFormat dt_trans = new SimpleDateFormat("yyyy-MM-dd");
        String transform = "";

        try {
            System.out.println(original);
            original = original.replaceAll("[ㄱ-ㅎㅏ-ㅣ가-힣]", "");
            Date date = null;
            switch(dataType){
                case "News":
                    original = original.replaceAll("-", "");
                    original = original.replaceAll("\\.", "");
                    System.out.println("[INFO] Regular: " + original);
                    date = dt_news.parse(original);
                    break;
                case "NaverBlog":
                    if(original.contains("-")){
                        original = original.replaceAll("-", "");
                        original = original.replaceAll("\\.", "");
                    }else {
                        String[] temp_arr = original.split(". ");
                        if (temp_arr[1].length() < 2) {
                            temp_arr[1] = "0" + temp_arr[1];
                        }
                        if (temp_arr[2].length() < 2) {
                            temp_arr[2] = "0" + temp_arr[2];
                        }
                        String[] hourmin = temp_arr[3].split(":");
                        if (hourmin[0].length() < 2) {
                            hourmin[0] = "0" + hourmin[0];
                        }
                        original = temp_arr[0] + temp_arr[1] + temp_arr[2] + " " + hourmin[0] + ":" + hourmin[1];
                    }
                    date = dt_news.parse(original);
                    break;
                case "DaumBlog":
                    original = original.replaceAll("-", "");
                    original = original.replaceAll("\\.", "");
                    date = dt_news.parse(original);
                    break;
                case "Twitter":
                    original = original.replaceAll("-", "");
                    original = original.replaceAll("\\.", "");
                    date = dt_news.parse(original);
                    break;
                default:
                    System.out.println("[ERROR] Unknown DataType.");
                    exit(1);
            }
            transform = dt_trans.format(date);
        }catch(Exception e){
            System.out.println("[Error] Parse failure.");
            e.printStackTrace();
        }
        return transform;
    }
    public static String getCurrentDate(){
        SimpleDateFormat dt_trans = new SimpleDateFormat("yyyy-MM-dd");
        String curr_date = "";

        try{
            curr_date = dt_trans.format(new Date());
        }catch(Exception e){
            System.out.println("[Error] Parse failure.");
            e.printStackTrace();
        }
        return curr_date;
    }
}

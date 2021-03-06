package data.lab.knowledgegraph.service;
/*
 *
 * Data Lab - graph database organization.
 *
 */

import casia.isi.neo4j.common.CRUD;
import casia.isi.neo4j.common.Field;
import casia.isi.neo4j.common.NeoAccessor;
import casia.isi.neo4j.compose.NeoComposer;
import casia.isi.neo4j.model.Label;
import casia.isi.neo4j.model.RelationshipType;
import casia.isi.neo4j.search.NeoSearcher;
import casia.isi.neo4j.util.FileUtil;
import casia.isi.neo4j.util.JSONTool;
import casia.isiteam.zdr.wltea.analyzer.cfg.Configuration;
import casia.isiteam.zdr.wltea.analyzer.core.IKSegmenter;
import casia.isiteam.zdr.wltea.analyzer.core.Lexeme;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import data.lab.knowledgegraph.register.Neo4jProperties;
import org.apache.log4j.PropertyConfigurator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * @author Yc-Ma
 * @PACKAGE_NAME: data.lab.knowledgegraph.service
 * @Description: TODO(可视化数据接口实现)
 * @date 2020/4/29 22:58
 */
@Service
public class DataServiceImpl {

    private final static Logger logger = LoggerFactory.getLogger(DataServiceImpl.class);

    @Autowired
    private Neo4jProperties neo4jProperties;

    private NeoSearcher neoSearcher;
    private NeoComposer neoComposer;

    /**
     * @param
     * @return
     * @Description: TODO(初始化)
     */
    public void initLoad() {
        if (neoSearcher == null) {
            logger.info("SERVER:" + neo4jProperties.getBolt() + " USER:" + neo4jProperties.getUsername() + " PWD:" + neo4jProperties.getPassword());
        }
        neoSearcher = new NeoSearcher(neo4jProperties.getBolt(), neo4jProperties.getUsername(), neo4jProperties.getPassword());
        neoComposer = new NeoComposer(neo4jProperties.getBolt(), neo4jProperties.getUsername(), neo4jProperties.getPassword());
    }

    /**
     * @param
     * @return
     * @Description: TODO(通过最顶层父标签加载标签树)
     */
    public JSONArray getLabelSpanningTree() {
        initLoad();
        String cypher = "MATCH (k:LabelsTree) WHERE k.hierarchy='1' OR  k.hierarchy=1\n" +
                "CALL apoc.path.spanningTree(k,{labelFilter:'+LabelsTree', maxLevel:3, optional:true, filterStartNode:true}) yield path return path";
//        return DbUtil.getLabelSpanningTree(cypher);
        return Objects.requireNonNull(null);
    }

    /**
     * @param
     * @return
     * @Description: TODO(关键词搜索)
     */
    public JSONObject searchZdrInfo(String name) {
        initLoad();
        StringBuilder builder = new StringBuilder();

        builder.append("match p=(n)-[]-(m) where n.name=~'.*" + name + ".*' return p limit 300 ");

        String cypher = builder.toString().substring(0, builder.length() - 10);
        JSONObject result = neoSearcher.execute(cypher, CRUD.RETRIEVE);
        return JSONTool.transferToOtherD3(result);
    }

    /**
     * @param
     * @return
     * @Description: TODO(搜索的问答处理响应)
     */
    public JSONObject searchAsk(String cypher) {
        initLoad();
        JSONObject result = neoSearcher.execute(cypher, CRUD.RETRIEVE);
        return JSONTool.transferToOtherD3(result);
    }

    /**
     * @param
     * @return
     * @Description: TODO(多实体之间关系搜索)
     */
    public JSONObject searchZdrRelaInfo(JSONArray nameArray, JSONArray sysIds) {
        initLoad();
        StringBuilder builder = new StringBuilder();

        for (int i = 0; i < nameArray.size(); i++) {
            Object entityOne = nameArray.get(i);
            for (int j = i + 1; j < nameArray.size(); j++) {
                Object entityTwo = nameArray.get(j);

                builder.append("match p=(n)-[]-(m) where n.name=~'.*" + entityOne + ".*' and m.name=~'.*" + entityTwo + ".*' return p limit 50 union all ");
            }
        }

        String cypher = builder.toString().substring(0, builder.length() - 10);
        JSONObject result = neoSearcher.execute(cypher, CRUD.RETRIEVE);
        return JSONTool.transferToOtherD3(result);
    }

    /**
     * @param name:实体名称的碎片
     * @param sysIds:系统用户ID
     * @Description: TODO(通过实体名模糊推荐与搜索关联信息)
     */
    public JSONObject searchInfo(String name, JSONArray sysIds) {
        initLoad();
        StringBuilder builder = new StringBuilder();

        builder.append("match p=(n)-[]-(m) where n.name CONTAINS '" + name + "' return p limit 50 union all ");
        String cypher = builder.toString().substring(0, builder.length() - 10);

        if ("load-all".equals(name)) {
            cypher = "MATCH p=()-[]->() RETURN p LIMIT 3000";
        }
        if (name != null && !"".equals(name) && name.contains("=QA")) {
            cypher = qaCypher(name.replace("=QA", ""));
        }

        JSONObject result = neoSearcher.execute(cypher, CRUD.RETRIEVE);
        return JSONTool.transferToOtherD3(result);
    }

    private String qaCypher(String name) {
        List<String> words = ikAnalyzer(name);
        StringBuilder builder = new StringBuilder();
        for (String wordF : words) {
            for (String wordT : words) {
                builder.append("match p=(n)-[]-(m) where n.name CONTAINS '")
                        .append(wordF)
                        .append("' AND n.name CONTAINS '")
                        .append(wordT)
                        .append("'  return p limit 50 union all ");
            }
        }
        return builder.substring(0, builder.length() - 11);
    }

    private List<String> ikAnalyzer(String text) {
        PropertyConfigurator.configureAndWatch("dic" + File.separator + "log4j.properties");
        Configuration cfg = new Configuration(true);

        StringReader input = new StringReader(text.trim());
        IKSegmenter ikSegmenter = new IKSegmenter(input, cfg);

        List<String> results = new ArrayList<>();
        try {
            for (Lexeme lexeme = ikSegmenter.next(); lexeme != null; lexeme = ikSegmenter.next()) {
                results.add(lexeme.getLexemeText());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return results;
    }

    public void loadCsv(String label) {
        initLoad();

        String nodesCsvName = "node-test.csv";
        String relationsCsvName = "relation-test.csv";

        // =======================================================生成NODE=======================================================


        /**
         * @param csvName:CSV文件名（数据写入CSV的顺序需要和方法传入参数顺序保持一致）String _uniqueField, String... _key
         * @param label:节点标签
         * @param _uniqueField:合并的唯一字段
         * @param _key:MERGE的属性字段
         * @return
         * @Description: TODO(导入节点CSV)
         */
        System.out.println(neoComposer.executeImportCsv(1000, nodesCsvName, Label.label(label), Field.UNIQUEUUID.getSymbolValue(),
                Field.ENTITYNAME.getSymbolValue(), "comment", "count"));

        // 所有节点设置name属性
        neoComposer.execute("MATCH (n) SET n.name=n._entity_name", CRUD.UPDATE);

        // =======================================================生成关系=======================================================
        System.out.println(neoComposer.executeImportCsv(1000, relationsCsvName, RelationshipType.withName("好友"), Label.label("Person"),
                Label.label("Person"), Field.UNIQUEUUID.getSymbolValue(), Field.UNIQUEUUID.getSymbolValue(), "current_time", "comment"));
    }

    public void loadGraphByCypher() {
        initLoad();
        // LOAD CSV
        try {
            mergeNodes();
            mergeRelationship();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void mergeRelationship() throws IOException {
        List<String> relList = relList = FileUtil.readFileByLine("neo-import-csv\\relation-user-defined.csv");

        // 创建关系
        for (String line : relList) {
            if (line != null && !"".equals(line)) {
                try {
                    String[] array = line.trim().split(",");
                    String startId = array[0];
                    String endId = array[1];
                    String rel = array[2];
                    String cypher = "MATCH (n),(m) WHERE n.id='" + startId + "' AND m.id='" + endId + "' MERGE p=(n)-[:" + rel + "]->(m);";
                    System.out.println(neoComposer.execute(cypher, CRUD.MERGE) + " CYPHER:" + cypher);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void mergeNodes() throws IOException {
        List<String> nodeList = FileUtil.readFileByLine("neo-import-csv\\node-user-defined.csv");
        // 创建节点
        for (String line : nodeList) {
            if (line != null && !"".equals(line)) {
                try {
                    String[] array = line.trim().split(",");
                    String id = array[0];
                    String name = array[1];
                    String label = array[2];
                    String cypher = "MERGE (n:" + label + " {id:'" + id + "'}) SET n.name='" + name + "';";
                    System.out.println(neoComposer.execute(cypher, CRUD.MERGE) + " CYPHER:" + cypher);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

}


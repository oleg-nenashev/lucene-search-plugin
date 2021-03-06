package org.jenkinsci.plugins.lucene.search.databackend;

import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.webapp.WebAppContext;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.URISyntaxException;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;

public class SolrSearchBackendTest {
    private static final File SOLR_WORK_DIR = new File("target/solr-for-test/").getAbsoluteFile();

    @Rule
    public JenkinsRule rule = new JenkinsRule();

    static int solrPort;
    private static ExecutorService backgroundWorker;
    private static JenkinsSearchBackend jenkinsSearchBackend;
    private Server server;

    @BeforeClass
    public static void setup() throws Exception {
        solrPort = findFreePort();
        backgroundWorker = Executors.newFixedThreadPool(1);
    }

    private void setupSolr() throws Exception {
        FileUtils.deleteQuietly(SOLR_WORK_DIR);
        FileUtils.copyDirectory(new File("src/test/resources/solr/"), SOLR_WORK_DIR);
        String configSolrXml = "<solr>" + "  <solrcloud>" + "    <str name=\"host\">${host:}</str>"
                + "    <int name=\"hostPort\">" + solrPort + "</int>"
                + "    <str name=\"hostContext\">${hostContext:solr}</str>"
                + "    <int name=\"zkClientTimeout\">${zkClientTimeout:30000}</int>"
                + "    <bool name=\"genericCoreNodeNames\">${genericCoreNodeNames:true}</bool>" + "  </solrcloud>"
                + "  <shardHandlerFactory name=\"shardHandlerFactory\"" + "    class=\"HttpShardHandlerFactory\">"
                + "    <int name=\"socketTimeout\">${socketTimeout:0}</int>"
                + "    <int name=\"connTimeout\">${connTimeout:0}</int>" + "  </shardHandlerFactory>" + "</solr>";
        File solrConfig = new File(SOLR_WORK_DIR, "solr.xml");
        OutputStream out = new FileOutputStream(solrConfig);
        out.write(configSolrXml.getBytes("UTF-8"));
        out.close();
        server = new Server(solrPort);

        final WebAppContext context = new WebAppContext();
        context.setResourceBase(SOLR_WORK_DIR.getPath());
        context.setContextPath("/");
        context.setClassLoader(Thread.currentThread().getContextClassLoader());
        System.setProperty("solr.solr.home", SOLR_WORK_DIR.getPath());
        server.setHandler(context);
        server.start();
        jenkinsSearchBackend.setSolrBackend(false, solrPort);
    }

    @Before
    public void before() throws Exception {
        jenkinsSearchBackend = new JenkinsSearchBackend(rule, backgroundWorker);
        setupSolr();
    }

    @AfterClass
    public static void tearDownClass() {
        backgroundWorker.shutdownNow();
    }

    @After
    public void tearDown() throws Exception {
        server.stop();
    }

    private static int findFreePort() throws IOException {
        Random rnd = new Random();
        for (int i = 0; i < 100; i++) {
            int testPort = rnd.nextInt(64000) + 1024;
            try {
                ServerSocket serverSocket = new ServerSocket(testPort);
                serverSocket.close();
                return testPort;
            } catch (IOException ex) {
            }
        }
        throw new IOException("Could not find available port");
    }

    @Test
    public void givenSolrWhenJobsWithBuildsAreExecutedThenTheyShouldBeSearchable() throws IOException,
            ExecutionException, InterruptedException, SAXException, URISyntaxException, TimeoutException {
        CommonTestCases.givenSearchWhenJobsWithBuildsAreExecutedThenTheyShouldBeSearchable(jenkinsSearchBackend, rule);
    }

    @Test
    public void givenSolrWhenIsNewItShouldSupportRebuildFromClean() throws IOException, ExecutionException,
            InterruptedException, SAXException, URISyntaxException {
        CommonTestCases.givenSearchWhenIsNewItShouldSupportRebuildFromClean(jenkinsSearchBackend, rule);
    }
}

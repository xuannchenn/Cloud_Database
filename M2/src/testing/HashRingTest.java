package testing;

import client.KVStore;
import app_kvServer.KVServer;
import ecs.ECS;
import ecs.ECSNode;
import ecs.IECSNode;
import ecs.ECSHashRing;
import org.apache.zookeeper.data.Stat;
import org.junit.Test;
import junit.framework.TestCase;

import app_kvECS.ECSClient;
import org.apache.zookeeper.*;

import java.io.IOException;
import java.math.BigInteger;

import java.util.*;
import java.util.concurrent.CountDownLatch;


public class HashRingTest extends TestCase {
    private ECSHashRing hr_test;
    private CountDownLatch connectedSignal = new CountDownLatch(1);
    private int numGetClients = 5;

    public void setUp() {
        try {
            hr_test = new ECSHashRing();
        } catch (Exception e) {
            //System.out.println("ECS Test error "+e);
        }
    }

    public void tearDown() {
        hr_test.removeAllNode();
        hr_test = null;
    }

    public void test_addNodes() {
        Exception ex = null;
        try {
            hr_test.addNode(new ECSNode("server1", "localhost", 50001));
            hr_test.addNode(new ECSNode("server2", "localhost", 50002));
            hr_test.addNode(new ECSNode("server3", "localhost", 50003));
        } catch (Exception e) {
            ex = e;
        }
        assertNull(ex);
    }

    public void test_getNodes() {
        Exception ex = null;
        try {
            ECSNode temp = new ECSNode("server4", "localhost", 50004);
            hr_test.getNodeByServerName("server4");
            hr_test.getNodeByHash(temp.getNodeHashBI());
        } catch (Exception e) {
            ex = e;
        }
        assertNull(ex);
    }

    public void test_removeNodes() {
        Exception ex = null;
        try {
            hr_test.removeNode(hr_test.getNodeByServerName("server4"));
        } catch (Exception e) {
            ex = e;
        }
        assertNull(ex);
    }
}
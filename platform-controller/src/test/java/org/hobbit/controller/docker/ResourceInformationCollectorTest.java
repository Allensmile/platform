package org.hobbit.controller.docker;

import static org.junit.Assert.assertNotNull;

import org.hobbit.core.Constants;
import org.hobbit.core.data.usage.ResourceUsageInformation;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.EnvironmentVariables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ResourceInformationCollectorTest extends ContainerManagerBasedTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(ResourceInformationCollectorTest.class);

    @Rule
    public final EnvironmentVariables environmentVariables = new EnvironmentVariables();

    @Before
    public void setEnv() {
        environmentVariables.set(ResourceInformationCollector.PROMETHEUS_HOST_KEY, "localhost");
        environmentVariables.set(ResourceInformationCollector.PROMETHEUS_PORT_KEY,
                ResourceInformationCollector.PROMETHEUS_PORT_DEFAULT);
    }

    @Test
    public void test() throws Exception {
        LOGGER.info("Creating container...");
        String containerId = manager.startContainer(busyboxImageName, Constants.CONTAINER_TYPE_SYSTEM, null,
                sleepCommand);
        assertNotNull(containerId);
        tasks.add(containerId);

        Thread.sleep(10000);

        ResourceInformationCollector collector = new ResourceInformationCollector(manager);
        LOGGER.info("Requesting usage information...");
        ResourceUsageInformation usage = collector.getSystemUsageInformation();

        Assert.assertNotNull(usage);
        LOGGER.info("Got usage information {}", usage);

        Assert.assertNotNull(usage.getCpuStats());
        Assert.assertTrue(usage.getCpuStats().getTotalUsage() > 0);
        Assert.assertNotNull(usage.getMemoryStats());
        Assert.assertTrue(usage.getMemoryStats().getUsageSum() > 0);
        Assert.assertNotNull(usage.getDiskStats());
        Assert.assertTrue(usage.getDiskStats().getFsSizeSum() > 0);

        // Generate a second container
        LOGGER.info("Creating second container...");
        containerId = manager.startContainer(busyboxImageName, Constants.CONTAINER_TYPE_SYSTEM, null, sleepCommand);
        assertNotNull(containerId);
        tasks.add(containerId);

        Thread.sleep(10000);

        LOGGER.info("Requesting usage information...");
        ResourceUsageInformation usage2 = collector.getSystemUsageInformation();
        Assert.assertNotNull(usage2);
        LOGGER.info("Got usage information {}", usage2);

        Assert.assertNotNull(usage2.getCpuStats());
        Assert.assertTrue(usage2.getCpuStats().getTotalUsage() > 0);
        Assert.assertTrue(usage.getCpuStats().getTotalUsage() <= usage2.getCpuStats().getTotalUsage());
        Assert.assertNotNull(usage2.getMemoryStats());
        Assert.assertTrue(usage.getMemoryStats().getUsageSum() <= usage2.getMemoryStats().getUsageSum());
        Assert.assertNotNull(usage2.getDiskStats());
        Assert.assertTrue(usage.getDiskStats().getFsSizeSum() <= usage2.getDiskStats().getFsSizeSum());
    }

    @Test
    public void testIncreasingFsSize() throws Exception {
        ResourceInformationCollector collector = new ResourceInformationCollector(manager);
        final String[] command = { "sh", "-c",
                "sleep 20s ; dd if=/dev/zero of=file.txt count=16024 bs=1048576 ; sleep 60s" };
        LOGGER.info("Creating container...");
        String containerId = manager.startContainer(busyboxImageName, Constants.CONTAINER_TYPE_SYSTEM, null, command);
        assertNotNull(containerId);
        tasks.add(containerId);
        // Waiting for the service to create and start its container
        Thread.sleep(10000);

        LOGGER.info("Requesting usage information...");
        ResourceUsageInformation usage = collector.getSystemUsageInformation();

        Assert.assertNotNull(usage);
        LOGGER.info("Got usage information {}", usage);

        Assert.assertNotNull(usage.getCpuStats());
        Assert.assertTrue(usage.getCpuStats().getTotalUsage() > 0);
        Assert.assertNotNull(usage.getMemoryStats());
        Assert.assertTrue(usage.getMemoryStats().getUsageSum() > 0);
        Assert.assertNotNull(usage.getDiskStats());
        Assert.assertTrue(usage.getDiskStats().getFsSizeSum() > 0);

        LOGGER.info("Waiting for the container to generate its file...");
        Thread.sleep(30000);

        LOGGER.info("Requesting usage information...");
        ResourceUsageInformation usage2 = collector.getSystemUsageInformation();
        Assert.assertNotNull(usage2);
        LOGGER.info("Got usage information {}", usage2);

        Assert.assertNotNull(usage2.getCpuStats());
        Assert.assertTrue(usage2.getCpuStats().getTotalUsage() > 0);
        // Assert.assertTrue("We expected that the CPU time used to generate the file
        // would increase the overall CPU time",
        // usage.getCpuStats().getTotalUsage() < usage2.getCpuStats().getTotalUsage());
        Assert.assertNotNull(usage2.getMemoryStats());
        // Assert.assertTrue(usage.getMemoryStats().getUsageSum() <
        // usage2.getMemoryStats().getUsageSum());
        Assert.assertNotNull(usage2.getDiskStats());
        // Assert.assertTrue("We expected that the Fssize would be increased when
        // generating a huge file",
        // usage.getDiskStats().getFsSizeSum() < usage2.getDiskStats().getFsSizeSum());
        Assert.assertTrue("We expected that the consumed memory would be increased when generating a huge file",
                (usage.getMemoryStats().getUsageSum()
                        + usage.getDiskStats().getFsSizeSum()) < (usage2.getMemoryStats().getUsageSum()
                                + usage2.getDiskStats().getFsSizeSum()));
    }
}

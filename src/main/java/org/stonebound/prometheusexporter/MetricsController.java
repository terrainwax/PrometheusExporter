package org.stonebound.prometheusexporter;

import com.google.common.collect.Iterables;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.Gauge;
import io.prometheus.client.exporter.common.TextFormat;
import net.minecraft.server.MinecraftServer;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.world.World;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class MetricsController extends AbstractHandler {
    private final PrometheusExporter exporter;

    private Gauge players = Gauge.build().name("mc_players_total").help("Total online and max players").labelNames("state").create().register();
    private Gauge tps = Gauge.build().name("mc_tps").help("Tickrate").labelNames("state").create().register();
    private Gauge loadedChunks = Gauge.build().name("mc_loaded_chunks").help("Chunks loaded per world").labelNames("world").create().register();
    private Gauge playersOnline = Gauge.build().name("mc_players_online").help("Players currently online per world").labelNames("world").create().register();
    private Gauge entities = Gauge.build().name("mc_entities").help("Entities loaded per world").labelNames("world").create().register();
    private Gauge tileEntities = Gauge.build().name("mc_tile_entities").help("Entities loaded per world").labelNames("world").create().register();
    private Gauge memory = Gauge.build().name("mc_jvm_memory").help("JVM memory usage").labelNames("type").create().register();

    public MetricsController(PrometheusExporter exporter) {
        this.exporter = exporter;
    }

    public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!target.equals("/metrics")) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        players.labels("online").set(Sponge.getGame().getServer().getOnlinePlayers().size());
        players.labels("max").set(Sponge.getGame().getServer().getMaxPlayers());

        for (World world : Sponge.getGame().getServer().getWorlds()) {
            loadedChunks.labels(world.getName()).set(Iterables.size(world.getLoadedChunks()));
            playersOnline.labels(world.getName()).set(world.getPlayers().size());
            entities.labels(world.getName()).set(world.getEntities().size());
            tileEntities.labels(world.getName()).set(world.getTileEntities().size());
        }
        double meanTickTime = MetricsController.mean(((MinecraftServer) Sponge.getServer()).tickTimeArray) * 1.0E-6D;

        tps.labels("tps").set(Sponge.getGame().getServer().getTicksPerSecond());
        tps.labels("meanticktime").set(meanTickTime);
        memory.labels("max").set(Runtime.getRuntime().maxMemory());
        memory.labels("free").set(Runtime.getRuntime().freeMemory());
        memory.labels("used").set(Runtime.getRuntime().maxMemory() - Runtime.getRuntime().freeMemory());

        try {
            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentType(TextFormat.CONTENT_TYPE_004);

            TextFormat.write004(response.getWriter(), CollectorRegistry.defaultRegistry.metricFamilySamples());

            baseRequest.setHandled(true);
        } catch (IOException e) {
            exporter.getLogger().error("Failed to read server statistics", e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    private static long mean(long[] values)
    {
        long sum = 0l;
        for (long v : values)
        {
            sum+=v;
        }

        return sum / values.length;
    }
}

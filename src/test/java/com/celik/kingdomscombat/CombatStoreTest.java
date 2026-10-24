package com.celik.kingdomscombat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

final class CombatStoreTest {
    record Snapshot(int writer,String payload){}

    @Test void concurrentWritersAndReadersAlwaysSeeWholeCommittedSnapshots(@TempDir Path dir)throws Exception{
        var target=dir.resolve("state.json");var start=new CountDownLatch(1);
        try(var executor=Executors.newFixedThreadPool(8)){
            var futures=new ArrayList<Future<?>>();
            for(int writer=0;writer<8;writer++){
                final int id=writer;
                futures.add(executor.submit(()->{
                    start.await();
                    for(int n=0;n<30;n++){
                        // Equivalent paths must not create separate staging-file transactions.
                        var path=id%2==0?target:dir.resolve(".").resolve("state.json");
                        CombatStore.write(path,new Snapshot(id,Integer.toString(id).repeat(8192)));
                        var read=CombatStore.read(path,Snapshot.class,null);
                        assertNotNull(read);assertEquals(Integer.toString(read.writer()).repeat(8192),read.payload());
                    }
                    return null;
                }));
            }
            start.countDown();
            for(var future:futures)future.get(30,TimeUnit.SECONDS);
        }
        try(var files=Files.list(dir)){assertEquals(List.of("state.json"),files.map(p->p.getFileName().toString()).toList());}
    }

    @Test void legacyStagingFileIsNeverConsumedOrOverwritten(@TempDir Path dir)throws Exception{
        var legacy=dir.resolve("state.json.tmp");Files.writeString(legacy,"preserve recovery data");
        CombatStore.write(dir.resolve("state.json"),new Snapshot(7,"saved"));
        assertEquals("preserve recovery data",Files.readString(legacy));
        assertEquals(7,CombatStore.read(dir.resolve("state.json"),Snapshot.class,null).writer());
    }

    @Test void failedPublicationCleansOnlyOwnedTemporaryFilesAndCanRetry(@TempDir Path dir)throws Exception{
        var target=dir.resolve("state.json");Files.createDirectory(target);
        var blocker=target.resolve("keep");Files.writeString(blocker,"untouched");
        var legacy=dir.resolve("state.json.tmp");Files.writeString(legacy,"old recovery");
        assertThrows(IllegalStateException.class,()->CombatStore.write(target,new Snapshot(1,"blocked")));
        assertEquals("untouched",Files.readString(blocker));assertEquals("old recovery",Files.readString(legacy));
        try(var files=Files.list(dir)){assertEquals(2,files.count());}
        Files.delete(blocker);Files.delete(target);
        CombatStore.write(target,new Snapshot(2,"recovered"));
        assertEquals("recovered",CombatStore.read(target,Snapshot.class,null).payload());
    }
}

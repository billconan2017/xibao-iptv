package com.xibao.iptv;
import org.junit.Test;
import static org.junit.Assert.*;
public class RemotePressGateTest {
    @Test public void ignoresLongPressRepeatsAndMislabelledIrBursts() {
        RemotePressGate gate=new RemotePressGate();
        assertTrue(gate.accept(0,1000));
        for(int i=1;i<40;i++){assertFalse(gate.accept(i,1000+i*10));assertFalse(gate.accept(0,1000+i*10));}
        assertTrue(gate.accept(0,1500));
        assertFalse(gate.accept(1,2500));
        assertTrue(gate.accept(0,3000));
    }
}

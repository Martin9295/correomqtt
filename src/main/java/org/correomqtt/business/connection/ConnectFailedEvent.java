package org.correomqtt.business.connection;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.correomqtt.business.eventbus.Event;
import org.correomqtt.business.eventbus.SubscribeFilter;

@AllArgsConstructor
@Getter
public class ConnectFailedEvent implements Event {
    private String connectionId;
    private Throwable throwable;

    @SubscribeFilter(value ="connectionId")
    public String getConnectionId(){
        return this.connectionId;
    }
}
package kim.biryeong.dantashader.displayhud;

import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.EntityAttachment;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

import java.util.Collection;

final class OwnerOnlyEntityAttachment extends EntityAttachment {
    private final ServerPlayer owner;
    private boolean ownerWatchingEnabled;

    OwnerOnlyEntityAttachment(ElementHolder holder, ServerPlayer owner) {
        super(holder, owner, true);
        this.owner = owner;
        this.attach();
    }

    @Override
    public void updateCurrentlyTracking(Collection<ServerGamePacketListenerImpl> currentlyTracking) {
        if (this.isRemoved()) {
            return;
        }

        for (ServerGamePacketListenerImpl tracking : currentlyTracking) {
            if (!isOwner(tracking)) {
                this.holder().stopWatching(tracking);
            }
        }

        if (this.ownerWatchingEnabled) {
            this.holder().startWatching(this.owner);
            this.refreshMount();
        }
    }

    @Override
    public void updateTracking(ServerGamePacketListenerImpl tracking) {
        if (this.isRemoved()) {
            return;
        }

        if (isOwner(tracking)) {
            this.restartForOwner();
        } else {
            this.holder().stopWatching(tracking);
        }
    }

    @Override
    public void startWatching(ServerPlayer player) {
        if (this.ownerWatchingEnabled && player == this.owner) {
            super.startWatching(player);
            this.refreshMount();
        }
    }

    @Override
    public void startWatching(ServerGamePacketListenerImpl handler) {
        if (this.ownerWatchingEnabled && isOwner(handler)) {
            super.startWatching(handler);
            this.refreshMount();
        }
    }

    void enableOwnerWatching() {
        this.ownerWatchingEnabled = true;
        this.holder().startWatching(this.owner);
        this.refreshMount();
    }

    void restartForOwner() {
        if (!this.ownerWatchingEnabled || this.isRemoved() || this.owner.hasDisconnected()) {
            return;
        }

        this.holder().stopWatching(this.owner);
        this.holder().startWatching(this.owner);
        this.refreshMount();
    }

    void refreshMount() {
        if (!this.isRemoved() && !this.owner.hasDisconnected()) {
            this.owner.connection.send(new ClientboundSetPassengersPacket(this.owner));
        }
    }

    private boolean isOwner(ServerGamePacketListenerImpl handler) {
        return handler == this.owner.connection;
    }
}

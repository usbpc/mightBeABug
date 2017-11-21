import org.slf4j.LoggerFactory
import sun.misc.Request
import sx.blah.discord.api.internal.json.objects.EmbedObject
import sx.blah.discord.handle.obj.*
import sx.blah.discord.util.*
import java.awt.Color
import java.util.concurrent.Future

//private val futureWaiterPool = newFixedThreadPoolContext(5, "Future Waiter Pool")
private val logger = LoggerFactory.getLogger("usbbot.util.IMessageExtensionsKT")

fun getDefaultEmbed(color: Color, message: String) : EmbedObject =
        getDefaultEmbedBuilder(color).withDescription(message).build()

fun getDefaultEmbedBuilder(color: Color) : EmbedBuilder =
        EmbedBuilder().withColor(color)

fun IChannel.checkPermissions(user: IUser, vararg permissions: Permissions) : Boolean {
    return if (PermissionUtils.hasPermissions(this, user, *permissions)) {
        true
    } else {
        //MessageSending.sendMessage(this, "Nope, don't have permissions!")
        false
    }
}

fun IGuild.checkPermissions(user: IUser, vararg permissions: Permissions) : Boolean =
        PermissionUtils.hasPermissions(this, user, *permissions)

fun IGuild.checkOurPermissions(vararg permissions: Permissions) : Boolean =
        this.checkPermissions(this.client.ourUser, *permissions)

inline fun IGuild.checkOurPermissions(vararg permissions: Permissions, block: () -> Any) : Boolean {
    return if (!this.checkOurPermissions(*permissions)) {
        block()
        false
    } else {
        true
    }
}

fun IGuild.checkOurPermissionOrSendError(channel: IChannel, vararg permissions: Permissions) : Boolean {
    return this.checkOurPermissions(*permissions) {
        val builder = StringBuilder("Missing Permissions: ").append("```")
        permissions.forEach { builder.append('\n').append(it) }
        builder.append("```")
        channel.sendError(builder.toString())
    }
}

fun IChannel.checkOurPermissions(vararg permissions: Permissions) : Boolean =
        this.checkPermissions(this.client.ourUser, *permissions)

inline fun IChannel.checkOurPermissions(vararg permissions: Permissions, block: () -> Any) : Boolean {
    return if (!this.checkOurPermissions(*permissions)) {
        block()
        false
    } else {
        true
    }
}

fun IChannel.checkOurPermissionsOrSendError(channel: IChannel, vararg permissions: Permissions) : Boolean {
    return this.checkOurPermissions(*permissions) {
        val builder = StringBuilder("Missing Permissions: ").append("```")
        permissions.forEach { builder.append('\n').append(it) }
        builder.append("```")
        channel.sendError(builder.toString())
    }
}

fun Future<IMessage>.updateSuccess(message: String) : Future<IMessage> =
        this.get().bufferedEdit(getDefaultEmbed(Color.GREEN, message))

fun Future<IMessage>.updateSuccess(embed: EmbedObject) : Future<IMessage> =
        this.get().bufferedEdit(embed)

fun Future<IMessage>.updateError(message: String) : Future<IMessage> =
        this.get().bufferedEdit(getDefaultEmbed(Color.RED, message))

fun Future<IMessage>.uptadeProcessing(message: String) : Future<IMessage> =
        this.get().bufferedEdit(getDefaultEmbed(Color.YELLOW, message))

fun IChannel.sendSuccess(message: String) : Future<IMessage> =
        this.bufferedSend(getDefaultEmbed(Color.GREEN, message))

fun IChannel.sendProcessing(message: String) =
        this.bufferedSend(getDefaultEmbed(Color.YELLOW, message))

fun IChannel.sendError(message: String) : Future<IMessage> =
        this.bufferedSend(getDefaultEmbed(Color.RED, message))

fun IMessage.bufferedEdit(embed: EmbedObject) : Future<IMessage> =
        RequestBuffer.request <IMessage> { this.edit(embed) }

fun IChannel.bufferedSend(embed: EmbedObject) : Future<IMessage> =
        RequestBuffer.request <IMessage> {
            var success = false
            var msg : IMessage? = null
            while (!success) {
                try {
                    msg = this.sendMessage(embed)
                    success = true
                } catch (ex: DiscordException) {
                    if (ex.errorMessage != "Message was unable to be sent (Discord didn't return a response)") {
                        throw ex
                    } else {
                        logger.error("Got an exception, but ignoring it.", ex)
                    }
                }
            }
            msg
        }

fun IMessage.bufferedDelete() : Future<Unit> =
        RequestBuffer.request <Unit> { this.delete() }

fun MessageHistory.bufferedBulkDelete() : Future<List<IMessage>> =
        RequestBuffer.request <List<IMessage>> { this.bulkDelete() }

fun IUser.bufferedMoveToVoiceChannel(channel: IVoiceChannel) =
        RequestBuffer.request { this.moveToVoiceChannel(channel) }


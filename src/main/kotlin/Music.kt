import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason
import com.sedmelluq.discord.lavaplayer.track.playback.AudioFrame
import sx.blah.discord.handle.audio.AudioEncodingType
import sx.blah.discord.handle.audio.IAudioProvider
import sx.blah.discord.handle.obj.IChannel
import sx.blah.discord.handle.obj.IGuild
import sx.blah.discord.handle.obj.IMessage
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue

class Music {

    val guildMusicMap = HashMap<Long, GuildMusicManager>()
    val playerManager : AudioPlayerManager = DefaultAudioPlayerManager()

    init {
        playerManager.enableGcMonitoring()
        AudioSourceManagers.registerRemoteSources(playerManager)
    }

    fun play(msg: IMessage) {

        if (msg.client.ourUser.getVoiceStateForGuild(msg.guild).channel == null) {
            val autorChannel = msg.author.getVoiceStateForGuild(msg.guild).channel
            if (autorChannel == null) {
                msg.channel.sendError("You are not in a voice channel!")
                return
            } else {
                autorChannel.join()
            }
        }

        val guildMusicManager = guildMusicMap.getOrPut(msg.guild.longID) {
            val musicManager = GuildMusicManager(playerManager, msg.guild)
            msg.guild.audioManager
            msg.guild.audioManager.audioProvider = musicManager.audioProvider
            musicManager
        }

        playerManager.loadItemOrdered(guildMusicManager, "https://www.youtube.com/watch?v=zOWJqNPeifU", MyAudioLoadResultHandler(msg.channel, guildMusicManager, "https://www.youtube.com/watch?v=zOWJqNPeifU"))
    }
}

class MyData(val requestChannel: IChannel)

class MyAudioLoadResultHandler(val channel: IChannel, val musicManager: GuildMusicManager, val searchParam: String) : AudioLoadResultHandler {

    override fun loadFailed(exception: FriendlyException) {
        if (exception.severity != FriendlyException.Severity.FAULT) {
            channel.sendError("Sorry, I can't play that Video:\n```${exception.message}```")
        } else {
            channel.sendError("Something went horribly Wrong:\n```${exception.message}```")
        }
    }

    override fun trackLoaded(track: AudioTrack) {
        track.userData = MyData(channel)
        musicManager.scheduler.queue(track)
        channel.sendSuccess("Added ${track.info.title} to the queue!")
    }

    override fun noMatches() {
        channel.sendError("Couldn't find anything for $searchParam")
    }

    override fun playlistLoaded(playlist: AudioPlaylist) {
        var firstTrack = playlist.selectedTrack

        if (firstTrack == null) {
            firstTrack = playlist.tracks[0]
        }

        channel.sendSuccess("Added ${firstTrack.info.title} (first track of playlist ${playlist.name})")

        firstTrack.userData = MyData(channel)
        musicManager.scheduler.queue(firstTrack)
    }

}

/**
 * This is a wrapper around AudioPlayer which makes it behave as an IAudioProvider for D4J. As D4J calls canProvide
 * before every call to provide(), we pull the frame in canProvide() and use the frame we already pulled in
 * provide().
 */
class AudioProvider
/**
 * @param audioPlayer Audio player to wrap.
 */
(private val audioPlayer: AudioPlayer) : IAudioProvider {
    private var lastFrame: AudioFrame? = null

    override fun isReady(): Boolean {
        if (lastFrame == null) {
            lastFrame = audioPlayer.provide()
        }

        return lastFrame != null
    }

    override fun provide(): ByteArray? {
        if (lastFrame == null) {
            lastFrame = audioPlayer.provide()
        }

        val data = if (lastFrame != null) lastFrame?.data else null
        lastFrame = null

        return data
    }

    override fun getChannels(): Int = 2

    override fun getAudioEncodingType(): AudioEncodingType = AudioEncodingType.OPUS
}

/**
 * Holder for both the player and a track scheduler for one guild.
 */
class GuildMusicManager
/**
 * Creates a player and a track scheduler.
 * @param manager Audio player manager to use for creating the player.
 */
(manager: AudioPlayerManager, guild: IGuild) {
    /**
     * Audio player for the guild.
     */
    val player: AudioPlayer = manager.createPlayer()
    /**
     * Track scheduler for the player.
     */
    val scheduler: TrackScheduler

    /**
     * @return Wrapper around AudioPlayer to use it as an AudioSendHandler.
     */
    val audioProvider: AudioProvider
        get() = AudioProvider(player)

    init {
        scheduler = TrackScheduler(player, guild)
        player.addListener(scheduler)
    }
}

/**
 * This class schedules tracks for the audio player. It contains the queue of tracks.
 */
class TrackScheduler
/**
 * @param player The audio player this scheduler uses
 */
(private val player: AudioPlayer, val guild: IGuild) : AudioEventAdapter() {
    val queue: BlockingQueue<AudioTrack>

    init {
        this.queue = LinkedBlockingQueue()
    }


    /**
     * Add the next track to queue or play right away if nothing is in the queue.
     *
     * @param track The track to play or add to queue.
     */
    fun queue(track: AudioTrack) {
        // Calling startTrack with the noInterrupt set to true will start the track only if nothing is currently playing. If
        // something is playing, it returns false and does nothing. In that case the player was already playing so this
        // track goes to the queue instead.
        if (!player.startTrack(track, true)) {
            queue.offer(track)
        }
    }

    /**
     * Start the next track, stopping the current one if it is playing.
     */
    fun nextTrack() {
        // Start the next track, regardless of if something is already playing or not. In case queue was empty, we are
        // giving null to startTrack, which is a valid argument and will simply stop the player.
        if (!queue.isEmpty()) {
            player.startTrack(queue.poll(), false)
        } else {
            guild.client.ourUser.getVoiceStateForGuild(guild).channel.leave()
        }
    }

    override fun onTrackStart(player: AudioPlayer, track: AudioTrack) {
        (track.userData as MyData).requestChannel.sendSuccess("Now playing ${track.info.title}")
    }

    override fun onTrackStuck(player: AudioPlayer, track: AudioTrack, thresholdMs: Long) {
        nextTrack()
        (track.userData as MyData).requestChannel.sendError("The Music got stuck, skipping to next song...")
    }

    override fun onTrackException(player: AudioPlayer, track: AudioTrack, exception: FriendlyException) {
        nextTrack()
        (track.userData as MyData).requestChannel.sendError("Something went wrong while playing ${track.info.title}:\n```${exception.message}```\nWill skip to the next song.")
    }

    override fun onTrackEnd(player: AudioPlayer, track: AudioTrack, endReason: AudioTrackEndReason) {
        // Only start the next track if the end reason is suitable for it (FINISHED or LOAD_FAILED)
        if (endReason.mayStartNext) {
            nextTrack()
        }
    }
}
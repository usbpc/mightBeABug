import sx.blah.discord.api.events.EventSubscriber;
import sx.blah.discord.handle.impl.events.guild.channel.message.MessageReceivedEvent;

public class MyEvents {
	private Music music;
	MyEvents() {
		music = new Music();
	}
	@EventSubscriber
	public void onMessageReceived(MessageReceivedEvent event){
		if(event.getMessage().getContent().startsWith(BotUtils.BOT_PREFIX + "test"))
			music.play(event.getMessage());
	}

}
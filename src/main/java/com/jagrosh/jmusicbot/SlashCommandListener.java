/*
 * Copyright 2025 Alex Yau (TheMajoris)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jagrosh.jmusicbot;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandClient;
import com.jagrosh.jmusicbot.audio.AudioHandler;
import com.jagrosh.jmusicbot.audio.QueuedTrack;
import com.jagrosh.jmusicbot.audio.RequestMetadata;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.lang.reflect.Field;
import java.util.List;

/**
 * Listener to handle slash command interactions by routing them to existing
 * commands.
 */
public class SlashCommandListener extends ListenerAdapter {
    private final CommandClient commandClient;
    private final Bot bot;

    public SlashCommandListener(CommandClient commandClient, Bot bot) {
        this.commandClient = commandClient;
        this.bot = bot;
    }

    @Override
    public void onSlashCommand(SlashCommandEvent event) {
        // Acknowledge the interaction immediately
        event.deferReply().queue();

        try {
            // Find the command
            Command command = findCommand(event.getName());
            if (command == null) {
                event.getHook().editOriginal("❌ Command not found: " + event.getName()).queue();
                return;
            }

            // Build arguments string
            StringBuilder argsBuilder = new StringBuilder();
            if (event.getOptions() != null && !event.getOptions().isEmpty()) {
                event.getOptions().forEach(option -> {
                    if (argsBuilder.length() > 0)
                        argsBuilder.append(" ");
                    argsBuilder.append(option.getAsString());
                });
            }

            // Execute command with custom logic for each command type
            executeCommand(command, event, argsBuilder.toString());

        } catch (Exception e) {
            event.getHook().editOriginal("❌ Error executing slash command: " + e.getMessage()).queue();
            e.printStackTrace();
        }
    }

    /**
     * Execute a command by calling custom handlers for each command type
     */
    private void executeCommand(Command command, SlashCommandEvent event, String arguments) {
        try {
            String commandName = command.getName().toLowerCase();

            switch (commandName) {
                case "play":
                    handlePlayCommand(event, arguments);
                    break;
                case "skip":
                    handleSkipCommand(event, arguments);
                    break;
                case "queue":
                    handleQueueCommand(event, arguments);
                    break;
                case "nowplaying":
                case "np":
                    handleNowPlayingCommand(event, arguments);
                    break;
                case "stop":
                    handleStopCommand(event, arguments);
                    break;
                case "pause":
                    handlePauseCommand(event, arguments);
                    break;
                default:
                    // Generic response for other commands
                    event.getHook().editOriginal("✅ Command received: **" + command.getName() + "**" +
                            (arguments.isEmpty() ? "" : " with arguments: `" + arguments + "`") +
                            "\nNote: This command is being processed with basic slash command support.").queue();
                    break;
            }
        } catch (Exception e) {
            event.getHook().editOriginal("❌ Error in command execution: " + e.getMessage()).queue();
            e.printStackTrace();
        }
    }

    /**
     * Handle play command specifically
     */
    private void handlePlayCommand(SlashCommandEvent event, String arguments) {
        try {
            if (arguments.isEmpty()) {
                event.getHook().editOriginal("❌ Please provide a song name or URL to play!").queue();
                return;
            }

            // Check if user is in voice channel
            if (event.getMember() == null || event.getMember().getVoiceState() == null ||
                    event.getMember().getVoiceState().getChannel() == null) {
                event.getHook().editOriginal("❌ You must be in a voice channel to use this command!").queue();
                return;
            }

            // Set up audio handler for the guild
            bot.getPlayerManager().setUpHandler(event.getGuild());

            // Process the search query - add "ytsearch:" prefix for plain text searches
            String searchQuery = arguments;
            if (!arguments.startsWith("http") && !arguments.startsWith("ytsearch:") &&
                    !arguments.startsWith("scsearch:") && !arguments.startsWith("spotify:")) {
                searchQuery = "ytsearch:" + arguments;
            }

            // Show loading message
            event.getHook().editOriginal("🔄 Loading... `[" + arguments + "]`").queue();

            // Use PlayerManager to load the track
            bot.getPlayerManager().loadItemOrdered(event.getGuild(), searchQuery,
                    new SlashPlayResultHandler(event, arguments, bot));

        } catch (Exception e) {
            event.getHook().editOriginal("❌ Error executing play command: " + e.getMessage()).queue();
            e.printStackTrace();
        }
    }

    /**
     * Handle skip command
     */
    private void handleSkipCommand(SlashCommandEvent event, String arguments) {
        try {
            AudioHandler handler = (AudioHandler) event.getGuild().getAudioManager().getSendingHandler();

            if (handler == null) {
                event.getHook().editOriginal("❌ No music is currently playing!").queue();
                return;
            }

            if (handler.getPlayer().getPlayingTrack() == null) {
                event.getHook().editOriginal("❌ Nothing is currently playing!").queue();
                return;
            }

            String title = handler.getPlayer().getPlayingTrack().getInfo().title;
            handler.getPlayer().stopTrack();
            event.getHook().editOriginal("⏭️ Skipped: **" + title + "**").queue();

        } catch (Exception e) {
            event.getHook().editOriginal("❌ Error executing skip command: " + e.getMessage()).queue();
            e.printStackTrace();
        }
    }

    /**
     * Handle queue command
     */
    private void handleQueueCommand(SlashCommandEvent event, String arguments) {
        try {
            AudioHandler handler = (AudioHandler) event.getGuild().getAudioManager().getSendingHandler();

            if (handler == null) {
                event.getHook().editOriginal("🎵 The queue is currently empty and nothing is playing!").queue();
                return;
            }

            if (handler.getQueue().isEmpty()) {
                AudioTrack current = handler.getPlayer().getPlayingTrack();
                if (current == null) {
                    event.getHook().editOriginal("🎵 The queue is currently empty and nothing is playing!").queue();
                } else {
                    event.getHook().editOriginal("🎵 **Now Playing:** " + current.getInfo().title +
                            "\n\nThe queue is currently empty.").queue();
                }
                return;
            }

            StringBuilder queueBuilder = new StringBuilder("🎵 **Current Queue:**\n");
            AudioTrack current = handler.getPlayer().getPlayingTrack();
            if (current != null) {
                queueBuilder.append("**Now Playing:** ").append(current.getInfo().title).append("\n\n");
            }

            for (int i = 0; i < Math.min(10, handler.getQueue().size()); i++) {
                QueuedTrack track = handler.getQueue().get(i);
                queueBuilder.append("`").append(i + 1).append(".` **")
                        .append(track.getTrack().getInfo().title).append("**\n");
            }

            if (handler.getQueue().size() > 10) {
                queueBuilder.append("\n... and ").append(handler.getQueue().size() - 10).append(" more tracks");
            }

            event.getHook().editOriginal(queueBuilder.toString()).queue();

        } catch (Exception e) {
            event.getHook().editOriginal("❌ Error showing queue: " + e.getMessage()).queue();
            e.printStackTrace();
        }
    }

    /**
     * Handle nowplaying command
     */
    private void handleNowPlayingCommand(SlashCommandEvent event, String arguments) {
        try {
            AudioHandler handler = (AudioHandler) event.getGuild().getAudioManager().getSendingHandler();

            if (handler == null || handler.getPlayer().getPlayingTrack() == null) {
                event.getHook().editOriginal("❌ Nothing is currently playing!").queue();
                return;
            }

            AudioTrack track = handler.getPlayer().getPlayingTrack();
            String nowPlaying = "🎵 **Now Playing:** " + track.getInfo().title;
            if (track.getInfo().author != null && !track.getInfo().author.isEmpty()) {
                nowPlaying += " by " + track.getInfo().author;
            }

            event.getHook().editOriginal(nowPlaying).queue();

        } catch (Exception e) {
            event.getHook().editOriginal("❌ Error getting current track: " + e.getMessage()).queue();
            e.printStackTrace();
        }
    }

    /**
     * Handle stop command
     */
    private void handleStopCommand(SlashCommandEvent event, String arguments) {
        try {
            AudioHandler handler = (AudioHandler) event.getGuild().getAudioManager().getSendingHandler();

            if (handler == null) {
                event.getHook().editOriginal("❌ No audio handler found!").queue();
                return;
            }

            handler.getQueue().clear();
            handler.getPlayer().stopTrack();
            event.getGuild().getAudioManager().closeAudioConnection();

            event.getHook().editOriginal("⏹️ Stopped playback and cleared queue!").queue();

        } catch (Exception e) {
            event.getHook().editOriginal("❌ Error executing stop command: " + e.getMessage()).queue();
            e.printStackTrace();
        }
    }

    /**
     * Handle pause command
     */
    private void handlePauseCommand(SlashCommandEvent event, String arguments) {
        try {
            AudioHandler handler = (AudioHandler) event.getGuild().getAudioManager().getSendingHandler();

            if (handler == null || handler.getPlayer().getPlayingTrack() == null) {
                event.getHook().editOriginal("❌ Nothing is currently playing!").queue();
                return;
            }

            boolean isPaused = handler.getPlayer().isPaused();
            handler.getPlayer().setPaused(!isPaused);

            String action = isPaused ? "Resumed" : "Paused";
            String emoji = isPaused ? "▶️" : "⏸️";

            event.getHook().editOriginal(emoji + " " + action + " **" +
                    handler.getPlayer().getPlayingTrack().getInfo().title + "**").queue();

        } catch (Exception e) {
            event.getHook().editOriginal("❌ Error executing pause command: " + e.getMessage()).queue();
            e.printStackTrace();
        }
    }

    /**
     * Find a command by name using reflection
     */
    private Command findCommand(String name) {
        try {
            Field commandsField = commandClient.getClass().getDeclaredField("commands");
            commandsField.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<Command> commands = (List<Command>) commandsField.get(commandClient);

            return commands.stream()
                    .filter(cmd -> cmd.getName().equalsIgnoreCase(name) ||
                            (cmd.getAliases() != null &&
                                    java.util.Arrays.stream(cmd.getAliases())
                                            .anyMatch(alias -> alias.equalsIgnoreCase(name))))
                    .findFirst()
                    .orElse(null);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Result handler for slash play commands
     */
    private static class SlashPlayResultHandler implements AudioLoadResultHandler {
        private final SlashCommandEvent event;
        private final String query;
        private final Bot bot;

        public SlashPlayResultHandler(SlashCommandEvent event, String query, Bot bot) {
            this.event = event;
            this.query = query;
            this.bot = bot;
        }

        @Override
        public void trackLoaded(AudioTrack track) {
            try {
                // Set up audio handler using the bot instance
                AudioHandler handler = bot.getPlayerManager().setUpHandler(event.getGuild());

                // Join voice channel if not already connected
                if (!event.getGuild().getAudioManager().isConnected()) {
                    event.getGuild().getAudioManager().openAudioConnection(
                            event.getMember().getVoiceState().getChannel());
                }

                RequestMetadata metadata = new RequestMetadata(event.getUser(),
                        new RequestMetadata.RequestInfo(query, track.getInfo().uri));
                handler.addTrack(new QueuedTrack(track, metadata));

                event.getHook().editOriginal("✅ **Added to queue:** " + track.getInfo().title).queue();

            } catch (Exception e) {
                event.getHook().editOriginal("❌ Error adding track: " + e.getMessage()).queue();
                e.printStackTrace();
            }
        }

        @Override
        public void playlistLoaded(AudioPlaylist playlist) {
            try {
                if (playlist.getTracks().isEmpty()) {
                    event.getHook().editOriginal("❌ The playlist is empty!").queue();
                    return;
                }

                // Set up audio handler
                AudioHandler handler = bot.getPlayerManager().setUpHandler(event.getGuild());

                // Join voice channel if not already connected
                if (!event.getGuild().getAudioManager().isConnected()) {
                    event.getGuild().getAudioManager().openAudioConnection(
                            event.getMember().getVoiceState().getChannel());
                }

                RequestMetadata metadata = new RequestMetadata(event.getUser(),
                        new RequestMetadata.RequestInfo(query, playlist.getTracks().get(0).getInfo().uri));

                if (playlist.isSearchResult()) {
                    // For search results, just add the first track
                    AudioTrack track = playlist.getTracks().get(0);
                    handler.addTrack(new QueuedTrack(track, metadata));
                    event.getHook().editOriginal("✅ **Added to queue:** " + track.getInfo().title).queue();
                } else {
                    // For actual playlists, add all tracks
                    int added = 0;
                    for (AudioTrack track : playlist.getTracks()) {
                        handler.addTrack(new QueuedTrack(track, metadata));
                        added++;
                        if (added >= 50)
                            break; // Limit to 50 tracks
                    }

                    event.getHook().editOriginal("✅ **Added " + added + " tracks to queue** from playlist: " +
                            playlist.getName()).queue();
                }

            } catch (Exception e) {
                event.getHook().editOriginal("❌ Error loading playlist: " + e.getMessage()).queue();
                e.printStackTrace();
            }
        }

        @Override
        public void noMatches() {
            event.getHook().editOriginal("❌ No matches found for: `" + query + "`").queue();
        }

        @Override
        public void loadFailed(FriendlyException exception) {
            event.getHook().editOriginal("❌ Failed to load track: " + exception.getMessage()).queue();
        }
    }
}
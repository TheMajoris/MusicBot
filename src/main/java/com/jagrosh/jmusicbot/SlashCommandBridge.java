/*
 * Copyright 2025 Alex Yau (TheMajoris)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jagrosh.jmusicbot;

import com.jagrosh.jdautilities.command.CommandClient;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

/**
 * Bridges slash commands to the existing CommandClient
 *
 * @author Alex Yau (TheMajoris)
 */
public class SlashCommandBridge extends ListenerAdapter {
    private final CommandClient client;

    public SlashCommandBridge(CommandClient client) {
        this.client = client;
    }

    @Override
    public void onSlashCommand(SlashCommandEvent event) {
        // Simply acknowledge the slash command and let Discord know we received it
        // This prevents the "The application did not respond" error
        event.reply("✅ Command received! Processing...").setEphemeral(true).queue();

        // Build the equivalent text command
        StringBuilder commandText = new StringBuilder();
        commandText.append(client.getPrefix()).append(event.getName());

        // Add options as arguments
        if (event.getOptions() != null && !event.getOptions().isEmpty()) {
            for (var option : event.getOptions()) {
                commandText.append(" ").append(option.getAsString());
            }
        }

        // Send the equivalent text command to the channel
        // The existing command system will process it normally
        event.getChannel().sendMessage(commandText.toString()).queue();
    }
}
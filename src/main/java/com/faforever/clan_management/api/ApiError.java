//SPDX-License-Identifier: MIT
package com.faforever.clan_management.api;

//the body of a refused api call, the sentence the service gave
public record ApiError(String message) {
}

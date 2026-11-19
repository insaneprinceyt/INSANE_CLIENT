# Friends & Worlds

INSANE CLIENT 2.2.0 can install [World Host](https://modrinth.com/mod/world-host) into
a compatible Fabric instance. World Host supplies the in-game friends list and
internet connection service. Android hosting and joining are experimental: a
successful mod installation does not establish that multiplayer works on your device.

## Before you start

- Both players need Microsoft accounts that own Minecraft Java Edition.
- Use the same Minecraft version and matching required mods. Fabric 1.21.1 is one
  supported example; choose the Java runtime appropriate for that version.
- Both players should install World Host to use its Friends tab.
- Both devices need an internet connection, and the host must keep the world running.

This setup supports **Fabric Java instances**. Rust Edition cannot load this Java mod.
Local and Ely.by accounts do not authenticate with World Host's supported friends
service. There is currently no supported World Host release for Minecraft 26.x.

## Install and connect

1. Select your Fabric instance and Microsoft account in INSANE CLIENT.
2. Open **Home → Friends & Worlds**. Check the instance and account shown under
   **Your setup**, then tap **Install World Host**.
3. After setup finishes, tap **Launch Minecraft**.
4. In Minecraft, open **Multiplayer → Friends → Add Friend**. Add your friend's
   Minecraft profile. Both players must add each other.
5. In World Host settings, enable **Whitelist Joins** and keep security at **Secure**.
   Whitelist Joins restricts direct-address and LAN joins as well as friend joins.
6. The host opens a singleplayer world, pauses and selects **Open to Friends**.
   World Host also provides **Share World** from the world selection screen.
7. The guest opens **Multiplayer → Friends** and selects the host's shared world.

Friend management happens inside Minecraft. The launcher prepares and checks the
selected instance; it does not show live friend presence or send invitations itself.

### Joining with an address

The host can run `/worldhost ip` in Minecraft and share the resulting relay address.
The guest enters it under **Multiplayer → Direct Connection**. A compatible guest can
join this way without installing World Host, but still needs an authenticated Java
account and any mods required by that world. Whitelist Joins must permit the guest.
A shared address is not automatically private when Whitelist Joins is disabled.

## Setup checks and troubleshooting

| Message or symptom | What to do |
| --- | --- |
| No compatible World Host release | Choose another Fabric Java instance, such as 1.21.1. The launcher will not install an incompatible version. |
| Finish installing the instance | Return to Home and complete the selected instance's installation, then refresh Friends & Worlds. |
| Microsoft account required | Select a Microsoft account that owns Java Edition. Local or Ely.by accounts cannot be substituted for this service. |
| World Host is disabled | Open **Manage installed mods**, enable the existing copy and refresh. |
| A different World Host file is installed | Review the installed copy in the mod manager. Remove that file only if you intend to replace it, then refresh and install the compatible release. |
| Could not check or install World Host | Check internet access and free storage, review existing mod files and tap **Refresh**. The installer keeps existing mods intact on download or verification failure. |
| Friend's world is missing | Confirm that both players added each other, the host selected Open to Friends, and the host's Minecraft/world is still running. |
| Connection fails after installation | Check game versions, required mods, Microsoft authentication and World Host connection status. A relay outage, network restrictions or Android runtime compatibility can prevent joining. |

## Downloads, service and attribution

World Host is an optional download, not an included Minecraft copy. The launcher
queries [Modrinth project `2AIZDfYo`](https://api.modrinth.com/v2/project/2AIZDfYo)
for the exact Minecraft version and Fabric loader. It requires stable World Host
`0.5.0` or newer, verifies the selected JAR's published size and SHA-512 hash, and
checks its Fabric manifest before moving it into the instance's mods folder.
Existing World Host files are not silently replaced. A future release requiring
unsupported external dependencies will not be installed by this setup flow.

World Host normally connects to the third-party service `world-host.jemnetworks.com`.
Its availability is independent of INSANE's download website. The mod and service
are developed independently of INSANE CLIENT:

- [World Host source and MIT license](https://github.com/Gaming32/world-host)
- [World Host connection-server source](https://github.com/Gaming32/world-host-server-rust)
- [Modrinth download page](https://modrinth.com/mod/world-host)

INSANE CLIENT is not affiliated with Mojang, Microsoft or World Host's maintainers.

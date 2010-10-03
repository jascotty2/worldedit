// $Id$
/*
 * WorldEdit
 * Copyright (C) 2010 sk89q <http://www.sk89q.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
*/

import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Map;
import java.io.*;
import org.mozilla.javascript.*;
import com.sk89q.worldedit.*;

/**
 * Plugin entry point for Hey0's mod.
 *
 * @author sk89q
 */
public class WorldEdit extends Plugin {
    private final static String DEFAULT_ALLOWED_BLOCKS =
        "0,1,2,3,4,5,7,8,9,10,11,12,13,14,15,16,17,18,19,20,35,41,42,43," +
        "44,45,47,48,49,52,53,54,56,57,58,60,61,62,67,73,78,79,80,81,82,85";

    private static Logger logger = Logger.getLogger("Minecraft");
    private HashMap<String,WorldEditSession> sessions = new HashMap<String,WorldEditSession>();
    private HashMap<String,String> commands = new HashMap<String,String>();

    private PropertiesFile properties;
    private String[] allowedBlocks;

    /**
     * Construct an instance of the plugin.
     */
    public WorldEdit() {
        super();

        commands.put("/editpos1", "Set editing position #1");
        commands.put("/editpos2", "Set editing position #2");
        commands.put("/editundo", "Undo");
        commands.put("/editredo", "Redo");
        commands.put("/clearhistory", "Clear history");
        commands.put("/clearclipboard", "Clear clipboard");
        commands.put("/editsize", "Get size of selected region");
        commands.put("/editset", "<Type> - Set all  blocks inside region");
        commands.put("/editreplace", "<ID> <ToReplaceID> - Replace all existing blocks inside region");
        commands.put("/editoverlay", "<ID> - Overlay the area one layer");
        commands.put("/removeabove", "<Size> - Remove blocks above head");
        commands.put("/editcopy", "Copies the currently selected region");
        commands.put("/editpaste", "Pastes the clipboard");
        commands.put("/editpasteair", "Pastes the clipboard (with air)");
        commands.put("/editload", "[Filename] - Load .schematic into clipboard");
        commands.put("/editsave", "[Filename] - Save clipboard to .schematic");
        commands.put("/editfill", "<ID> <Radius> <Depth> - Fill a hole");
        commands.put("/script", "[Filename] <Args...> - Run a WorldEdit script");
    }

    /**
     * Gets the WorldEdit session for a player.
     *
     * @param player
     * @return
     */
    private WorldEditSession getSession(Player player) {
        if (sessions.containsKey(player.getName())) {
            return sessions.get(player.getName());
        } else {
            WorldEditSession session = new WorldEditSession();
            sessions.put(player.getName(), session);
            return session;
        }
    }

    /**
     * Get an item ID from an item name or an item ID number.
     *
     * @param id
     * @return
     * @throws UnknownItemException
     * @throws DisallowedItemException
     */
    private int getItem(String id, boolean allAllowed)
            throws UnknownItemException, DisallowedItemException {
        int foundID;

        try {
            foundID = Integer.parseInt(id);
        } catch (NumberFormatException e) {
            try {
                foundID = etc.getInstance().getDataSource().getItem(id);
            } catch (NumberFormatException e2) {
                throw new UnknownItemException();
            }
        }

        // All items allowed
        if (allAllowed || allowedBlocks[0].equals("")) {
            return foundID;
        }

        for (String s : allowedBlocks) {
            if (s.equals(String.valueOf(foundID))) {
                return foundID;
            }
        }

        throw new DisallowedItemException();
    }

    /**
     * Get an item ID from an item name or an item ID number.
     *
     * @param id
     * @return
     * @throws UnknownItemException
     * @throws DisallowedItemException
     */
    private int getItem(String id) throws UnknownItemException,
                                          DisallowedItemException {
        return getItem(id, false);
    }

    /**
     *
     * @param player
     */
    @Override
    public void onDisconnect(Player player) {
        sessions.remove(player.getName());
    }

    /**
     * Checks to make sure that there are enough arguments.
     *
     * @param args
     * @param min
     * @throws InsufficientArgumentsException
     */
    private void checkArgs(String[] args, int min) throws InsufficientArgumentsException {
        if (args.length <= min) {
            throw new InsufficientArgumentsException(String.format("Min. %d arguments required", min));
        }
    }

    /**
     * Enables the plugin.
     */
    public void enable() {
        if (properties == null) {
            properties = new PropertiesFile("worldedit.properties");
        } else {
            properties.load();
        }

        allowedBlocks = properties.getString("allowed-blocks", DEFAULT_ALLOWED_BLOCKS).split(",");

        etc controller = etc.getInstance();

        for (Map.Entry<String,String> entry : commands.entrySet()) {
            controller.addCommand(entry.getKey(), entry.getValue());
        }
    }

    /**
     * Disables the plugin.
     */
    public void disable() {
        etc controller = etc.getInstance();

        for (String key : commands.keySet()) {
            controller.removeCommand(key);
        }

        sessions.clear();
    }

    /**
     * 
     * @param player
     * @param split
     * @return
     */
    @Override
    public boolean onCommand(Player player, String[] split) {
        try {
            if (commands.containsKey(split[0])) {
                if (etc.getInstance().canUseCommand(player.getName(), split[0])) {
                    return handleEditCommand(player, split);
                }
            }

            return false;
        } catch (NumberFormatException e) {
            player.sendMessage(Colors.Rose + "Number expected; string given.");
            return true;
        } catch (IncompleteRegionException e2) {
            player.sendMessage(Colors.Rose + "The edit region has not been fully defined.");
            return true;
        } catch (UnknownItemException e3) {
            player.sendMessage(Colors.Rose + "Unknown item.");
            return true;
        } catch (DisallowedItemException e4) {
            player.sendMessage(Colors.Rose + "Disallowed item.");
            return true;
        } catch (InsufficientArgumentsException e5) {
            player.sendMessage(Colors.Rose + e5.getMessage());
            return true;
        }
    }

    /**
     * The main meat of command processing.
     * 
     * @param player
     * @param split
     * @return
     * @throws UnknownItemException
     * @throws IncompleteRegionException
     * @throws InsufficientArgumentsException
     * @throws DisallowedItemException
     */
    private boolean handleEditCommand(Player player, String[] split)
            throws UnknownItemException, IncompleteRegionException,
                   InsufficientArgumentsException, DisallowedItemException
    {
        WorldEditSession session = getSession(player);
        EditSession editSession = new EditSession();

        // Set edit position #1
        if (split[0].equalsIgnoreCase("/editpos1")) {
            session.setPos1((int)Math.floor(player.getX()),
                            (int)Math.floor(player.getY()),
                            (int)Math.floor(player.getZ()));
            player.sendMessage(Colors.LightPurple + "First edit position set.");
            return true;

        // Set edit position #2
        } else if (split[0].equalsIgnoreCase("/editpos2")) {
            session.setPos2((int)Math.floor(player.getX()),
                            (int)Math.floor(player.getY()),
                            (int)Math.floor(player.getZ()));
            player.sendMessage(Colors.LightPurple + "Second edit position set.");
            return true;

        // Undo
        } else if (split[0].equalsIgnoreCase("/editundo")) {
            if (session.undo()) {
                player.sendMessage(Colors.LightPurple + "Undo successful.");
            } else {
                player.sendMessage(Colors.Rose + "Nothing to undo.");
            }
            return true;

        // Redo
        } else if (split[0].equalsIgnoreCase("/editredo")) {
            if (session.redo()) {
                player.sendMessage(Colors.LightPurple + "Redo successful.");
            } else {
                player.sendMessage(Colors.Rose + "Nothing to redo.");
            }
            return true;

        // Clear undo history
        } else if (split[0].equalsIgnoreCase("/clearhistory")) {
            session.clearHistory();
            player.sendMessage(Colors.LightPurple + "History cleared.");
            return true;

        // Clear clipboard
        } else if (split[0].equalsIgnoreCase("/clearclipboard")) {
            session.setClipboard(null);
            player.sendMessage(Colors.LightPurple + "Clipboard cleared.");
            return true;

        // Paste
        } else if (split[0].equalsIgnoreCase("/editpasteair") ||
                   split[0].equalsIgnoreCase("/editpaste")) {
            if (session.getClipboard() == null) {
                player.sendMessage(Colors.Rose + "Nothing is in your clipboard.");
            } else {
                Point<Integer> pos = new Point<Integer>((int)Math.floor(player.getX()),
                                                        (int)Math.floor(player.getY()),
                                                        (int)Math.floor(player.getZ()));
                session.getClipboard().paste(editSession, pos,
                    split[0].equalsIgnoreCase("/editpaste"));
                session.remember(editSession);
                logger.log(Level.INFO, player.getName() + " used " + split[0]);
                player.sendMessage(Colors.LightPurple + "Pasted.");
            }

            return true;

        // Fill a hole
        } else if (split[0].equalsIgnoreCase("/editfill")) {
            checkArgs(split, 1);
            int blockType = getItem(split[1]);
            int radius = split.length > 2 ? Math.max(1, Integer.parseInt(split[2])) : 50;
            int depth = split.length > 3 ? Math.max(1, Integer.parseInt(split[3])) : 1;

            int cx = (int)Math.floor(player.getX());
            int cy = (int)Math.floor(player.getY());
            int cz = (int)Math.floor(player.getZ());
            int minY = Math.max(-128, cy - depth);

            int affected = fill(editSession, cx, cz, cx, cy, cz,
                                blockType, radius, minY);

            logger.log(Level.INFO, player.getName() + " used /editfill");
            player.sendMessage(Colors.LightPurple + affected + " block(s) have been created.");

            session.remember(editSession);

            return true;

        // Remove blocks above current position
        } else if (split[0].equalsIgnoreCase("/removeabove")) {
            int size = split.length > 1 ? Math.max(1, Integer.parseInt(split[1]) - 1) : 0;

            int affected = 0;
            int cx = (int)Math.floor(player.getX());
            int cy = (int)Math.floor(player.getY());
            int cz = (int)Math.floor(player.getZ());

            for (int x = cx - size; x <= cx + size; x++) {
                for (int z = cz - size; z <= cz + size; z++) {
                    for (int y = cy; y <= 127; y++) {
                        if (editSession.getBlock(x, y, z) != 0) {
                            editSession.setBlock(x, y, z, 0);
                            affected++;
                        }
                    }
                }
            }

            logger.log(Level.INFO, player.getName() + " used /removeabove");
            player.sendMessage(Colors.LightPurple + affected + " block(s) have been removed.");

            session.remember(editSession);

            return true;

        // Load .schematic to clipboard
        } else if (split[0].equalsIgnoreCase("/editload")) {
            checkArgs(split, 1);
            String filename = split[1].replace("\0", "") + ".schematic";
            File dir = new File("schematics");
            File f = new File("schematics", filename);

            try {
                String filePath = f.getCanonicalPath();
                String dirPath = dir.getCanonicalPath();

                if (!filePath.substring(0, dirPath.length()).equals(dirPath)) {
                    player.sendMessage(Colors.Rose + "Schematic could not read or it does not exist.");
                } else {
                    int cx = (int)Math.floor(player.getX());
                    int cy = (int)Math.floor(player.getY());
                    int cz = (int)Math.floor(player.getZ());
                    Point<Integer> origin = new Point<Integer>(cx, cy, cz);
                    session.setClipboard(RegionClipboard.loadSchematic(filePath, origin));
                    logger.log(Level.INFO, player.getName() + " loaded " + filePath);
                    player.sendMessage(Colors.LightPurple + filename + " loaded.");
                }
            } catch (SchematicLoadException e) {
                player.sendMessage(Colors.Rose + "Load error: " + e.getMessage());
            } catch (IOException e) {
                player.sendMessage(Colors.Rose + "Schematic could not read or it does not exist.");
            }

            return true;

        // Save clipboard to .schematic
        } else if (split[0].equalsIgnoreCase("/editsave")) {
            if (session.getClipboard() == null) {
                player.sendMessage(Colors.Rose + "Nothing is in your clipboard.");
                return true;
            }
            
            checkArgs(split, 1);
            String filename = split[1].replace("\0", "") + ".schematic";
            File dir = new File("schematics");
            File f = new File("schematics", filename);

            if (!dir.exists()) {
                if (!dir.mkdir()) {
                    player.sendMessage(Colors.Rose + "A schematics/ folder could not be created.");
                    return true;
                }
            }

            try {
                String filePath = f.getCanonicalPath();
                String dirPath = dir.getCanonicalPath();

                if (!filePath.substring(0, dirPath.length()).equals(dirPath)) {
                    player.sendMessage(Colors.Rose + "Invalid path for Schematic.");
                } else {
                    session.getClipboard().saveSchematic(filePath);
                    logger.log(Level.INFO, player.getName() + " saved " + filePath);
                    player.sendMessage(Colors.LightPurple + filename + " saved.");
                }
            } catch (IOException e) {
                player.sendMessage(Colors.Rose + "Schematic could not written.");
            }
            
            return true;

        // Run an editscript
        } else if (split[0].equalsIgnoreCase("/script")) {
            checkArgs(split, 1);
            String filename = split[1].replace("\0", "") + ".js";
            String[] args = new String[split.length - 2];
            System.arraycopy(split, 2, args, 0, split.length - 2);
            runScript(player, session, editSession, filename, args);
            return true;
        }

        int lowerX = session.getLowerX();
        int upperX = session.getUpperX();
        int lowerY = session.getLowerY();
        int upperY = session.getUpperY();
        int lowerZ = session.getLowerZ();
        int upperZ = session.getUpperZ();
        
        // Get size of area
        if (split[0].equalsIgnoreCase("/editsize")) {
            player.sendMessage(Colors.LightPurple + "# of blocks: " + getSession(player).getSize());
            return true;

        // Replace all blocks in the region
        } else if(split[0].equalsIgnoreCase("/editset")) {
            checkArgs(split, 1);
            int blockType = getItem(split[1]);
            int affected = 0;

            for (int x = lowerX; x <= upperX; x++) {
                for (int y = lowerY; y <= upperY; y++) {
                    for (int z = lowerZ; z <= upperZ; z++) {
                        editSession.setBlock(x, y, z, blockType);
                        affected++;
                    }
                }
            }

            logger.log(Level.INFO, player.getName() + " used /editset");
            player.sendMessage(Colors.LightPurple + affected + " block(s) have been set.");

            session.remember(editSession);

            return true;

        // Replace all blocks in the region
        } else if(split[0].equalsIgnoreCase("/editreplace")) {
            checkArgs(split, 1);
            int blockType = getItem(split[1]);
            int replaceType = split.length > 2 ? getItem(split[2], true) : -1;

            int affected = 0;

            for (int x = lowerX; x <= upperX; x++) {
                for (int y = lowerY; y <= upperY; y++) {
                    for (int z = lowerZ; z <= upperZ; z++) {
                        if ((replaceType == -1 && editSession.getBlock(x, y, z) != 0) ||
                            (editSession.getBlock(x, y, z) == replaceType)) {
                            editSession.setBlock(x, y, z, blockType);
                            affected++;
                        }
                    }
                }
            }

            logger.log(Level.INFO, player.getName() + " used /editreplace");
            player.sendMessage(Colors.LightPurple + affected + " block(s) have been replaced.");

            session.remember(editSession);

            return true;

        // Lay blocks over an area
        } else if (split[0].equalsIgnoreCase("/editoverlay")) {
            checkArgs(split, 1);
            int blockType = getItem(split[1]);

            // We don't want to pass beyond boundaries
            upperY = Math.min(127, upperY + 1);
            lowerY = Math.max(-128, lowerY - 1);

            int affected = 0;

            for (int x = lowerX; x <= upperX; x++) {
                for (int z = lowerZ; z <= upperZ; z++) {
                    for (int y = upperY; y >= lowerY; y--) {
                        if (y + 1 <= 127 && editSession.getBlock(x, y, z) != 0 && editSession.getBlock(x, y + 1, z) == 0) {
                            editSession.setBlock(x, y + 1, z, blockType);
                            affected++;
                            break;
                        }
                    }
                }
            }

            logger.log(Level.INFO, player.getName() + " used /editoverlay");
            player.sendMessage(Colors.LightPurple + affected + " block(s) have been overlayed.");

            session.remember(editSession);

            return true;

        // Copy
        } else if (split[0].equalsIgnoreCase("/editcopy")) {
            Point<Integer> min = new Point<Integer>(lowerX, lowerY, lowerZ);
            Point<Integer> max = new Point<Integer>(upperX, upperY, upperZ);
            Point<Integer> pos = new Point<Integer>((int)Math.floor(player.getX()),
                                                    (int)Math.floor(player.getY()),
                                                    (int)Math.floor(player.getZ()));

            RegionClipboard clipboard = new RegionClipboard(min, max, pos);
            clipboard.copy(editSession);
            session.setClipboard(clipboard);

            logger.log(Level.INFO, player.getName() + " used /editcopy");
            player.sendMessage(Colors.LightPurple + "Block(s) copied.");

            return true;
        }

        return false;
    }

    /**
     * Fills an area recursively in the X/Z directions.
     * 
     * @param editSession
     * @param x
     * @param z
     * @param cx
     * @param cy
     * @param cz
     * @param blockType
     * @param radius
     * @param minY
     * @return
     */
    private int fill(EditSession editSession, int x, int z, int cx, int cy,
                     int cz, int blockType, int radius, int minY) {
        double dist = Math.sqrt(Math.pow(cx - x, 2) + Math.pow(cz - z, 2));
        int affected = 0;
        
        if (dist > radius) {
            return 0;
        }

        if (editSession.getBlock(x, cy, z) == 0) {
            affected = fillY(editSession, x, cy, z, blockType, minY);
        } else {
            return 0;
        }
        
        affected += fill(editSession, x + 1, z, cx, cy, cz, blockType, radius, minY);
        affected += fill(editSession, x - 1, z, cx, cy, cz, blockType, radius, minY);
        affected += fill(editSession, x, z + 1, cx, cy, cz, blockType, radius, minY);
        affected += fill(editSession, x, z - 1, cx, cy, cz, blockType, radius, minY);

        return affected;
    }

    /**
     * Recursively fills a block and below until it hits another block.
     * 
     * @param editSession
     * @param x
     * @param cy
     * @param z
     * @param blockType
     * @param minY
     * @return
     */
    private int fillY(EditSession editSession, int x, int cy,
                      int z, int blockType, int minY) {
        int affected = 0;
        
        for (int y = cy; y > minY; y--) {
            if (editSession.getBlock(x, y, z) == 0) {
                editSession.setBlock(x, y, z, blockType);
                affected++;
            } else {
                break;
            }
        }

        return affected;
    }

    /**
     * Execute a script.
     *
     * @param player
     * @param filename
     * @param args
     */
    private boolean runScript(Player player, WorldEditSession session,
            EditSession editSession, String filename, String[] args) {
        File dir = new File("editscripts");
        File f = new File("editscripts", filename);

        try {
            String filePath = f.getCanonicalPath();
            String dirPath = dir.getCanonicalPath();

            if (!filePath.substring(0, dirPath.length()).equals(dirPath)) {
                player.sendMessage(Colors.Rose + "Script file does not exist.");
            } else if (!f.exists()) {
                player.sendMessage(Colors.Rose + "Script file does not exist.");
            } else {
                // Read file
                StringBuffer buffer = new StringBuffer();
                FileInputStream stream = new FileInputStream(f);
                BufferedReader in = new BufferedReader(new InputStreamReader(stream, "UTF-8"));
                int c;
                while ((c = in.read()) > -1) {
                    buffer.append((char)c);
                }
                in.close();
                String code = buffer.toString();

                // Evaluate
                Context cx = Context.enter();
                try {                    
                    ScriptableObject scope = cx.initStandardObjects();

                    // Add args
                    ScriptableObject.putProperty(scope, "args",
                        Context.javaToJS(args, scope));

                    // Add context
                    ScriptPlayer scriptPlayer = new ScriptPlayer(player);
                    ScriptContext context = new ScriptContext(
                        scriptPlayer);
                    ScriptableObject.putProperty(scope, "context",
                        Context.javaToJS(context, scope));
                    ScriptableObject.putProperty(scope, "player",
                        Context.javaToJS(scriptPlayer, scope));

                    // Add Minecraft context
                    ScriptMinecraftContext minecraft =
                        new ScriptMinecraftContext(editSession);
                    ScriptableObject.putProperty(scope, "minecraft",
                        Context.javaToJS(minecraft, scope));

                    cx.evaluateString(scope, code, filename, 1, null);
                    player.sendMessage(Colors.LightPurple + filename + " executed successfully.");

                    return true;
                } catch (RhinoException re) {
                    player.sendMessage(Colors.Rose + "JS error: " + re.getMessage());
                    re.printStackTrace();
                } finally {
                    Context.exit();
                    session.remember(editSession);
                }
            }
        } catch (IOException e) {
            player.sendMessage(Colors.Rose + "Script could not read or it does not exist.");
        }

        return false;
    }
}
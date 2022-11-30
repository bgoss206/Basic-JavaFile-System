/*
* Authors: Elijah Shaw & Braxton Goss
* Date: 6/8/2022
* Class: CSS430
*/

import java.util.*;

public class FileSystem {
    private SuperBlock superblock;
    private Directory directory;
    private FileTable filetable;

    // constructor for file system

    public FileSystem(int diskBlocks) {

        // initializes the superblock, directory, and filetable
        superblock = new SuperBlock(diskBlocks);
        directory = new Directory(superblock.totalInodes);
        filetable = new FileTable(directory);

        // read the "/" file from disk
        FileTableEntry dirEnt = open("/", "r");
        int dirSize = fsize(dirEnt);
        if (dirSize > 0) {
            // the directory has some data.
            byte[] dirData = new byte[dirSize];
            read(dirEnt, dirData);
            directory.bytes2directory(dirData);
        }
        close(dirEnt);
    }

    // sync the file system
    public void sync() {
        // open the root file in writing mode
        FileTableEntry entry = open("/", "w");
        
        // write the entry to directory
        write(entry, directory.directory2bytes());
        
        // close the entry
        close(entry);
        
        // sync the superblock
        superblock.sync();
    }

    // format all files in the directory
    public int format(int files){
        superblock.format(files); // call superblock's format 

        // reestablish table and directory to reformat all files
        directory = new Directory(superblock.totalInodes);
        filetable = new FileTable(directory);

        // return OK signal to caller
        return 0;
    }

    // open a file in a specified mode
    // return the created entry
    public FileTableEntry open(String fileName, String mode) {
        FileTableEntry entry = filetable.falloc(fileName, mode);
        if (mode.equals("w")) {
            if (deallocAllBlocks(entry) == false) {
                return null;
            } 
        }
        return entry;
    }

    // read from a FileTableEntry into a buffer
    public int read(FileTableEntry entry, byte[] buffer) {
        // if we're not in read mode cancel operation
        if (entry == null || entry.mode == "w" || entry.mode == "a") {
            return -1;
        }

        // start at 0
        int index = 0;

        // get size of buffer
        int bufferLength = buffer.length;

        // get size of file
        int fileSize  = fsize(entry);

        synchronized(entry) {
            // while we haven't read everything
            while (bufferLength > 0 && entry.seekPtr < fileSize) {

                // search for data target 
                int target = entry.inode.findTargetBlock(entry.seekPtr);
                // if target file is invalid for any reason, leave 
                if (target == -1) {
                    break;
                }

                // create new buffer array to be read into
                byte[] blockData = new byte[Disk.blockSize];
                
                // read data from target into newly created buffer array
                SysLib.rawread(target, blockData);

                // where to offset in the block based on the seekptr
                int offset = entry.seekPtr % Disk.blockSize;

                // what length of the block can we read
                int readableBlockLength = Disk.blockSize - offset;

                // what length of the file can we read
                int readableFileLength = fileSize - entry.seekPtr;
                
                // double check the buffer to read into isn't bigger than the block or the file
                int readableLength = Math.min(Math.min(readableBlockLength, bufferLength), readableFileLength);

                // read from block into buffer
                System.arraycopy(blockData, offset, buffer, index, readableLength);
                
                // update seekPtr now that we've read that length
                entry.seekPtr += readableLength;

                // update bufferIndex now that we've read that length
                index += readableLength;
                
                // update bufferLength now that we've read that length
                bufferLength -= readableLength;
            }

            // return location of seek pointer 
            return index;
        }
    }

    // write from a buffer into a FileTableEntry
    public int write(FileTableEntry entry, byte[] buffer) {
        // if we're not in write mode cancel operation and return error code of -1
        if (entry == null || entry.mode == "r") {
            return -1;
        }
        
        synchronized(entry) {
            int index = 0;
            int bufferLength = buffer.length;
            // while we havent written anything
            while (bufferLength > 0) {
                // find where we're going to write
                int target = entry.inode.findTargetBlock(entry.seekPtr);
                // create and register a new free block as the target block
                // if target is nonexistent/invalid
                if (target == -1) {

                    // get free block
                    short freeBlock = (short) superblock.getFreeBlock();

                    // attempt to write it into free block 
                    int attempt = entry.inode.registerTargetBlock(entry.seekPtr, freeBlock);

                    // if it doesn't work, panick on write 
                    switch (attempt) {
                        case -2:
                            // same thing as case -1
                        case -1:
                            SysLib.cerr("panicking on write\n");
                            return -1;
                        case -3:
                            if (!(entry.inode.registerIndexBlock((short)superblock.getFreeBlock()))) {
                                SysLib.cerr("panicking on write\n");
                                return -1;
                            }
                            if (entry.inode.registerTargetBlock(entry.seekPtr, freeBlock) != 0) {
                                SysLib.cerr("panicking on write\n");
                                return -1;
                            }
                            break;
                    }
                    // target is now set to the new written block
                    target = freeBlock;
                }

                // create buffer in blockData
                byte[] blockData = new byte[Disk.blockSize];

                // if it's empty, exit
                if (SysLib.rawread(target, blockData) == -1) {
                    System.exit(2);
                }

                // move offset by the block size
                int offset = entry.seekPtr % Disk.blockSize;

                // calculate minimum writable length between buffer and blocksize - offset #
                int writableLength = Math.min(Disk.blockSize - offset, bufferLength);

                // copy data between arrays
                System.arraycopy(buffer, index, blockData, offset, writableLength);

                // write the buffer into the target
                SysLib.rawwrite(target, blockData);

                // update pointers
                entry.seekPtr += writableLength;
                index += writableLength;
                bufferLength -= writableLength;

                // boundary check to ensure pointer is within inode bounds
                if (entry.seekPtr > entry.inode.length) {
                    entry.inode.length = entry.seekPtr;
                }
            }
            // write the inode to the disk
            entry.inode.toDisk(entry.iNumber);

            // return location of seek pointer
            return index;
        }
        
        
        
    }

    // sets the seek pointer to a position specified by offset and whence
    public int seek(FileTableEntry entry, int offset, int whence) {
        synchronized(entry) {

            // the whence tells us how to update
            switch(whence) {

                // move the seek pointer to offset
                case 0:
                    entry.seekPtr = offset;
                    break;

                // increase seek pointer by offset
                case 1: 
                    entry.seekPtr += offset;
                    break;

                // offset is assumed to be negative
                // move seek pointer from the end of the file by offset
                // if positive value is provided, this will go over the file
                // and checks after switch-case will snap it back to end of file. 
                case 2:
                    entry.seekPtr = entry.inode.length + offset;
                    break;

                // any other value of whence provided is invalid
                default:
                    return -1;
            }
            // If the user attempts to set the seek pointer to a negative number 
            // you must clamp it to zero.
            if (entry.seekPtr < 0) {
                entry.seekPtr = 0;
            }
            // If the user attempts to set the pointer to beyond the file size, 
            // you must set the seek pointer to the end of the file.
            if (entry.seekPtr > entry.inode.length) {
                entry.seekPtr = entry.inode.length;
            }
            // In both cases, you should return success.
            return entry.seekPtr;
        }
    }

    
    // close the passed FileTableEntry
    public boolean close(FileTableEntry entry) {
        synchronized(entry) {
            entry.count--;
            if (entry.count > 0) {
                return true;
            }
        }
        return filetable.ffree(entry);
    }

    // delete a file (closes the file entry and frees its inumber from directory)
    public boolean delete(String fileName) {
        FileTableEntry entry = open(fileName, "w");
        short iNumber = entry.iNumber;
        // return whether we successfully closed the file and freed it from directory
        return (close(entry) && directory.ifree(iNumber));
    }


    // get the size of the passed FileTableEntry
    public int fsize(FileTableEntry entry) {
        synchronized(entry) {
            return entry.inode.length;
        }
    }
    
    // private method - not used in any other external classes
    private boolean deallocAllBlocks(FileTableEntry entry){
        // make sure there's actually an entry to deallocate
        if (entry.inode.count != 1 || entry == null) {
            return false;
        }

        // unregister the entry's index block
        byte[] unregisteredBlock = entry.inode.unregisterIndexBlock();
        
        // make sure there's actually an unregistered block
        if (unregisteredBlock != null) {

            // variable to save it into
            short unregisteredBlockInShort;

            // return unregistered index
            while (true){
                unregisteredBlockInShort = SysLib.bytes2short(unregisteredBlock, 0);
                if (unregisteredBlockInShort == -1){
                    break;
                }else{
                    superblock.returnBlock((int)unregisteredBlockInShort);
                }   
            }
        }
        
        // return all blocks to the superblock
        int i = 0;
        Inode iNode = entry.inode;

        // return all valid blocks 
        while (i < Inode.directSize) {
            if (iNode.direct[i] != -1) {
                superblock.returnBlock((int)iNode.direct[i]);
                iNode.direct[i] = -1;
            }
            i++;
        }
        // write back to disk
        entry.inode.toDisk(entry.iNumber);
        return true;
    }
}
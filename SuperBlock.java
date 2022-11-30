/*
* Authors: Elijah Shaw & Braxton Goss
* Date: 6/2/2022
* Class: CSS430
*/

import java.util.*;

public class SuperBlock {
    private final int defaultInodeBlocks = 64;
    public int totalBlocks;
    public int totalInodes;
    public int freeList;

    // constructor
    public SuperBlock(int diskSize) {
        byte[] superBlock = new byte[Disk.blockSize];
        SysLib.rawread(0, superBlock);
        totalBlocks = SysLib.bytes2int(superBlock, 0);
        totalInodes = SysLib.bytes2int(superBlock, 4);
        freeList = SysLib.bytes2int(superBlock, 8);

        if (totalBlocks == diskSize && totalInodes > 0 && freeList >= 2) {
            // valid disk contents
            return;
        } else {
            // need to format disk
            totalBlocks = diskSize;
            format(defaultInodeBlocks);
        }
    }

    // format the superblock
    public void format(int iNodes) {
        // initialize the superblock
        totalInodes = iNodes;
        // initialize each inode and immediately write it back to disk
        for (short i = 0; i < totalInodes; i++) {
            Inode inode = new Inode();
            inode.flag = 0;
            inode.toDisk(i);
        }
        // initialize free blocks
        freeList = 2 + totalInodes * 32 / Disk.blockSize;
        for (int i = freeList; i < totalBlocks; i++) {
            byte[] superBlock = new byte[Disk.blockSize];
            for (int j = 0; j < Disk.blockSize; j++) {
                superBlock[j] = 0;
            }
            SysLib.int2bytes(i + 1, superBlock, 0);
            SysLib.rawwrite(i, superBlock);
        }
        sync();
        
    }

    // sync the superblock
    public void sync() {
        // write back in-memory superblock to disk: SysLib.rawwrite( 0, superblock );
        byte[] superBlock = new byte[Disk.blockSize];
        SysLib.int2bytes(totalBlocks, superBlock, 0);
        SysLib.int2bytes(totalInodes, superBlock, 4);
        SysLib.int2bytes(freeList, superBlock, 8);
        SysLib.rawwrite(0, superBlock);
    }

    // attempt to get a free block
    public int getFreeBlock() {
        // get a new free block from the free list                                                                  
        int freeBlockNumber = freeList;
        if (freeBlockNumber != -1) {
            byte[] superBlock = new byte[Disk.blockSize];
            SysLib.rawread(freeBlockNumber, superBlock);
            freeList = SysLib.bytes2int(superBlock, 0);
            SysLib.int2bytes(0, superBlock, 0);
            SysLib.rawwrite(freeBlockNumber, superBlock);
        }
        return freeBlockNumber;
    }

    // return a block to the free list
    public boolean returnBlock (int blockNumber) {
        // return this old block to the free list. The list can be a stack.
        if (blockNumber >= 0) {
            byte[] superBlock = new byte[Disk.blockSize];
            for (int i = 0; i < Disk.blockSize; i++) {
                superBlock[i] = 0;
            }
            SysLib.int2bytes(freeList, superBlock, 0);
            SysLib.rawwrite(blockNumber, superBlock);
            freeList = blockNumber;
            return true;
        }
        return false; 
    }
}
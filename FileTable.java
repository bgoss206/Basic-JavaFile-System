/*
* Authors: Elijah Shaw & Braxton Goss
* Date: 6/7/2022
* Class: CSS430
*/


import java.util.*;


public class FileTable { 
 
      private Vector<FileTableEntry> table;        // the actual entity of this file table 
      private Directory dir;       // the root directory  
 
    public FileTable( Directory directory ) { // constructor 
        table = new Vector<FileTableEntry>();     // instantiate a file (structure) table 
        dir = directory;           // receive a reference to the Directory 
    }                            // from the file system 
 
      // major public methods 
    public synchronized FileTableEntry falloc( String fileName, String mode ) { 
        // allocate a new file (structure) table entry for this file name 
        // allocate/retrieve and register the corresponding inode using dir 
        // increment this inode's count 
        // immediately write back this inode to the disk 
        // return a reference to this file (structure) table entry
        short iNumber;
        Inode iNode;
        while (true) {
            // if the fileName is at the directory root 
            if (fileName.equals("/")){
                iNumber = 0; // set to directory iNode location
            }else{
                iNumber = dir.namei(fileName);
            }
            if (iNumber >= 0) {
                iNode = new Inode(iNumber);
                // if in reading mode
                if (mode.equals("r")) {
                    // if used or unused 
                    if (iNode.flag == 0 || iNode.flag == 1) {
                        iNode.flag = 1;
                        break;
                    }
                    try {
                        wait();
                    } catch (InterruptedException ex) {
                        // just catch the exception
                    }
                } else {
                    // if its unused
                    if (iNode.flag == 0 || iNode.flag == 3) {
                        iNode.flag = 2;
                        break;
                    }
                    // if inode is used in r
                    if (iNode.flag == 1) {
                        iNode.flag = 4;
                    }
                    // if i node is used in !r
                    if (iNode.flag == 2) {
                        iNode.flag = 5;
                    }
                    // allocate iNumber to current INode
                    iNode.toDisk(iNumber);
                    try {
                        // wait for section to be finished
                        wait(); 
                    } catch (InterruptedException ex){
                    // encountered exceptions during debugging, just catches those exceptions
                    }
                }
            }else {
                // if the mode is not read, allocate new node and set the flag to used in !r
                if (!(mode.equals("r"))) {
                    iNumber = dir.ialloc(fileName);
                    iNode = new Inode();
                    iNode.flag = 2;
                    break;
                }else {
                    // otherwise, leave 
                    return null;
                }
            }
        }
        // increment inode count
        iNode.count++;

        // write the inode back to disk
        iNode.toDisk(iNumber);
        
        // make a new FileTableEntry and add to table
        FileTableEntry entry = new FileTableEntry(iNode, iNumber, mode);
        table.add(entry);
        return entry;
    }
 
      public synchronized boolean ffree( FileTableEntry entry ) { 
        // receive a file table entry reference
        boolean isFound = false;
        // free this file table entry.
        if (table.remove(entry)) {
            entry.inode.count--;

            // fetch flag from entry for comparison
            short entryFlag = entry.inode.flag;
            
            // r used or !r used --> unused
            if (entryFlag == 1 || entryFlag == 2) {
                entry.inode.flag = 0;
            // wreg used or wreg !used --> wreg unused 
            } else if (entryFlag == 4 || entryFlag == 5) {
                entry.inode.flag = 3;
            }
            
            // save the corresponding inode to the disk 
            entry.inode.toDisk(entry.iNumber);
            notify();
            isFound = true;
        }
        
        // return true if this file table entry found in my table
        return isFound;
      } 

    // return if table is empty 
    // should be called before starting a format 
    public synchronized boolean fempty( ) { 
        return table.isEmpty( );  
    }                            
} 
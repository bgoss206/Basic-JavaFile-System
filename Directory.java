/*
* Authors: Elijah Shaw & Braxton Goss
* Date: 6/8/2022
* Class: CSS430
*/

public class Directory
{

    // maximum allowable character length = 30
    private static int maxChars = 30;

    // arrays used to store sizes and names
    private int[] fsizes;
    private char[][] fnames;
    

    // constructor
    public Directory(int maxINumber) {

        // initialize fsizes
        fsizes = new int[maxINumber];
        for (int i = 0; i < maxINumber; ++i) {
            fsizes[i] = 0;
        }

        // construct 2D file name array fnames
        fnames = new char[maxINumber][maxChars];

        // root indicator
        String root = "/";

        // initial fsizes at root
        fsizes[0] = root.length(); 

        // copy root characters into fnames
        root.getChars(0, fsizes[0], fnames[0], 0);
    }

    // converts byte data into directory info
    public void bytes2directory(byte[] data) {
        // assumes data[] contains directory information retrieved from disk 
        
        // initialize the directory's fsizes[] with data from parameter
        int offset = 0;
        for (int i = 0; i < fsizes.length; i++, offset += 4) {
            fsizes[i] = SysLib.bytes2int(data, offset);
        }
        // initialize the directory's fnames[] with data from parameter
        for (int i = 0; i < fnames.length; i++, offset += maxChars * 2) {
            String fname = new String( data, offset, maxChars * 2);
            fname.getChars(0, fsizes[i], fnames[i], 0);
        }
    }

    // converts directory info into byte data
    public byte[] directory2bytes() {
        
        // converts and return directory information into a plain byte array 
        byte[] data = new byte[fsizes.length * 4 + fnames.length * maxChars * 2]; 
        int offset = 0; 
        for ( int i = 0; i < fsizes.length; i++, offset += 4 ) 
            SysLib.int2bytes( fsizes[i], data, offset ); 

        // this byte array will be written back to disk
        for ( int i = 0; i < fnames.length; i++, offset += maxChars * 2 ) { 
            String tableEntry = new String( fnames[i], 0, fsizes[i] ); 
            byte[] bytes = tableEntry.getBytes( ); 
            System.arraycopy( bytes, 0, data, offset, bytes.length ); 
        } 

        // return data[] array back to caller
        return data; 
    }


    // allocates space in the directory for file called fileName
    public short ialloc(String fileName) {
        // filename is the one of a file to be created. 
        
        int fileLength = fileName.length();

        // 1 up to fsizes.length because root directory at fsizes[0] 
        // should not be allocated to
        for (short i = 1; i < fsizes.length; i++) { 
            if (fsizes[i] == 0) {
                if (fileLength <= maxChars) {
                    fsizes[i] = fileLength;
                } else {
                    fsizes[i] = maxChars;
                }
                // allocates a new inode number for this filename
                fnames[i] = fileName.substring(0, fsizes[i]).toCharArray();
                return i;
            }
        }
        // return -1 for any error
        return -1;
    }

    // frees the inumber from directory
    public boolean ifree(short iNumber) {
        // deallocates this inumber (inode number) 
        // the corresponding file will be deleted.
        boolean canDeleteFile = false;
        if (fsizes[iNumber] > 0) {
            fsizes[iNumber] = 0;
            canDeleteFile = true;
        }

        // return result of free (true or false bool) 
        return canDeleteFile;
    }

    // finds the iNumber for a specific file
    public short namei(String fileName) {
        // returns the inumber corresponding to this filename 
        for (short i = 0; i < fsizes.length; i++) { 
            String currFileName = new String(fnames[i]);
            // if we find it, return the number
            if (fsizes[i] == fileName.length() && fileName.equals(currFileName)) {
                return i;
            }
        }
        // no file with fileName found in memory
        return -1;
    }


}
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class GtImages {
    List<GtImage> images = new ArrayList<>();
    DisplayShelf ds = new DisplayShelf();
    public void main(String file_name, String ds_file_name) throws Exception{
        File dir = new File(file_name);
        File[] directory_list = dir.listFiles();
        if (directory_list == null) {
            throw new Exception("Invalid directory");
        } else {
            for (File child : directory_list) {
                GtImage image = new GtImage();
                image.main(String.valueOf(child));
                images.add(image);
            }
        }
        ds.main(ds_file_name);
        for (GtImage image : images){
            image.set_ds(ds);
        }
    }
    public List<Float> difference(BufferedImage slice, int slice_num, Map<Integer, Integer> weights) {
        List<Float> differences = new ArrayList<>();
        for (GtImage image : images){
            float score = image.difference(slice, weights, slice_num);
            differences.add(score);
        }
        return differences;
    }
}

class GtImage {
    String name;
    BufferedImage image;
    List<BufferedImage> ds_images;
    int width; int height;
    public void main(String file_name) throws IOException {
        File file = new File(file_name);
        name = file_name;
        image = ImageIO.read(file);
        width = image.getWidth();
        height = image.getHeight();
    }
    public void set_ds(DisplayShelf ds) throws Exception {
        ds_images = new ArrayList<>();
        for (int slice_num = 0; slice_num < 4; slice_num++){
            int x; int y;
            switch (slice_num) {
                case 0 -> { x = 19; y = 16; }
                case 1 -> { x = 4; y = 16; }
                case 2 -> { x = 19; y = 11; }
                case 3 -> { x = 4; y = 11; }
                default -> throw new Exception("Invalid slice");
            }
            BufferedImage slice = ds.get_slice(slice_num);
            BufferedImage big_slice = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
            Graphics g = big_slice.getGraphics();
            g.drawImage(slice, 0, 0, 64, 64, null);
            g.drawImage(image, x, y, 45, 45, null);
            g.dispose();
            ds_images.add(big_slice);
        }
    }
    public BufferedImage ds_as_res(int res, int slice_num){
        BufferedImage res_image = new BufferedImage(res, res, BufferedImage.TYPE_INT_RGB);
        Graphics g = res_image.getGraphics();
        g.drawImage(ds_images.get(slice_num), 0, 0, res, res, null);
        g.dispose();
        return res_image;
    }
    public float difference(BufferedImage slice, Map<Integer, Integer> weights, int slice_num) {
        float score = 0;
        for (Integer key : weights.keySet()) {
            BufferedImage mini_image = ds_as_res(key, slice_num);
            BufferedImage mini_slice = new BufferedImage(key, key, BufferedImage.TYPE_INT_RGB);
            Graphics g = mini_slice.getGraphics();
            g.drawImage(slice, 0, 0, key, key, null);
            g.dispose();
            float res_score = 0;
            for (int y = 0; y < key; y++){
                for (int x = 0; x < key; x++){
                    int image_p = mini_image.getRGB(x, y);
                    int slice_p = mini_slice.getRGB(x, y);

                    int diff_r = Math.abs(((image_p >> 16) & 0xff) - ((slice_p >> 16) & 0xff));
                    int diff_g = Math.abs(((image_p >> 8 ) & 0xff) - ((slice_p >> 8 ) & 0xff));
                    int diff_b = Math.abs(( image_p        & 0xff) - ( slice_p        & 0xff));
                    float diff = diff_r + diff_g + diff_b;
                    diff /= 255 * 3;
                    res_score = res_score + diff;
                }
            }
            res_score = (float) (weights.get(key) * res_score / Math.pow(key, 2));
            score = score + res_score;
        }
        return score;
    }
}

class DisplayShelf {
    String ds_file_name;
    BufferedImage ds;
    public void main(String file_name) throws IOException {
        ds_file_name = file_name;
        ds = ImageIO.read(new File(ds_file_name));
    }
    public BufferedImage get_slice(int slice_num) throws Exception{
        int width = 16;
        int height = 16;
        int x; int y;

        switch (slice_num) {
            case 0 -> { x = 0; y = 0; }
            case 1 -> { x = 16; y = 0; }
            case 2 -> { x = 0; y = 16; }
            case 3 -> { x = 16; y = 16; }
            default -> throw new Exception("Invalid slice");
        }

        return ds.getSubimage(x, y, width, height);
    }
}

class AllocatedSlice {
    BufferedImage slice;
    int slice_num;
    public void main(BufferedImage slice_, int slice_num_){
        slice = slice_;
        slice_num = slice_num_;
    }
}

class BlockMatching {
    BufferedImage image;
    int stack_width; int stack_height;
    int stack_resolution;
    public void main(String file_name, int resolution) throws IOException {
        BufferedImage image_ = ImageIO.read(new File(file_name));
        stack_resolution = resolution;
        stack_width = Math.round((float) image_.getWidth() / resolution);
        stack_height = Math.round((float) image_.getHeight() / resolution);
        image = new BufferedImage(stack_width * resolution, stack_height * resolution, BufferedImage.TYPE_INT_RGB);
        Graphics g = image.getGraphics();
        g.drawImage(image_, 0, 0, stack_width * resolution, stack_height * resolution, null);
        g.dispose();
    }
    public List<AllocatedSlice> get_slices() {
        List<AllocatedSlice> slices = new ArrayList<>();
        for (int y = 0; y < stack_height; y++){
            for (int x = 0; x < stack_width; x++){
                int slice_num = 0;
                if ((y % 2) == 1){ slice_num += 2; }
                if ((x % 2) == 1){ slice_num += 1; }
                BufferedImage slice = image.getSubimage(x * stack_resolution, y * stack_resolution, stack_resolution, stack_resolution);
                AllocatedSlice a_slice = new AllocatedSlice();
                a_slice.main(slice, slice_num);
                slices.add(a_slice);
            }
        }
        return slices;
    }
    public List<GtImage> match(GtImages images, Map<Integer, Integer> weights) {
        List<GtImage> out_images = new ArrayList<>();
        List<AllocatedSlice> slices = get_slices();
        int counter = 0;
        for (AllocatedSlice a_slice : slices){
            counter ++;
            List<Float> penalties = images.difference(a_slice.slice, a_slice.slice_num, weights);
            int image_index = penalties.indexOf(Collections.min(penalties));
            GtImage out_image = images.images.get(image_index);
            out_images.add(out_image);
            System.out.printf("Blocks processed: %1$d / %2$d%n", counter, stack_height * stack_width);
        }
        return out_images;
    }
    public BufferedImage build(List<GtImage> images) {
        BufferedImage out_image = new BufferedImage(
                stack_width * 64, stack_height * 64,
                BufferedImage.TYPE_INT_RGB);
        Graphics g = out_image.getGraphics();
        int index_counter = 0;
        for (int y = 0; y < stack_height; y++){
            for (int x = 0; x < stack_width; x++){
                int slice_num = 0;
                if ((y % 2) == 1){ slice_num += 2; }
                if ((x % 2) == 1){ slice_num += 1; }
                GtImage gt_image = images.get(index_counter);
                g.drawImage(gt_image.ds_images.get(slice_num), x * 64, y * 64, null);
                index_counter ++;
            }
        }
        g.dispose();
        return out_image;
    }
}


public class Main {
    public static void main(String[] args) throws Exception {
        Map<Integer, Integer> weights = new HashMap<>();
        String image = args[0];
        String block_file = args[1];
        String display_shelf = args[2];
        String out_file = args[3];
        int pixel_per_block = Integer.parseInt(args[4]);
        int block_resolution = Integer.parseInt(args[5]);
        weights.put(block_resolution, 1);

        GtImages images = new GtImages();
        BlockMatching bm = new BlockMatching();
        images.main(block_file, display_shelf);
        bm.main(image, pixel_per_block);
        List<GtImage> out_images = bm.match(images, weights);
        BufferedImage out_image = bm.build(out_images);
        ImageIO.write(out_image, "PNG", new File(out_file));
    }
}

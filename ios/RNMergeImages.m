
#import "RNMergeImages.h"
#import <React/RCTConvert.h>

@implementation RNMergeImages

- (NSString *)saveImage:(UIImage *)image {
    NSString *fileName = [[NSProcessInfo processInfo] globallyUniqueString];
    NSString *fullPath = [NSString stringWithFormat:@"%@%@.jpg", NSTemporaryDirectory(), fileName];
    NSData *imageData = UIImagePNGRepresentation(image);
    [imageData writeToFile:fullPath atomically:YES];
    return fullPath;
}

- (NSURL *)applicationDocumentsDirectory
{
    return [[[NSFileManager defaultManager] URLsForDirectory:NSDocumentDirectory inDomains:NSUserDomainMask] lastObject];
}

- (dispatch_queue_t)methodQueue
{
    return dispatch_get_main_queue();
}
RCT_EXPORT_MODULE()

- (NSDictionary *)constantsToExport
{
    return @{
             @"size": @{
                    @"LARGEST": @"1",
                    @"SMALLEST": @"0",
                },
             @"target": @{
                 @"TEMP": @"1",
                 @"DISK": @"0",
                 },
             @"mergeType": @{
                     @"MERGE": @"0",
                     @"COLLAGE": @"1",
                     }
             };
}

RCT_EXPORT_METHOD(merge:(NSArray *)imagePaths
                  options:(NSDictionary *)options
                  resolver:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject)
{
    NSInteger mergeType = [[RCTConvert NSString:options[@"mergeType"]] integerValue];

    UIImage *newImage = nil;

    switch (mergeType) {
        case 0:
            newImage = [self merge: imagePaths];
            break;
        case 1:
            newImage = [self collage: imagePaths];
            break;
            
        default:
            newImage = [self merge: imagePaths];
            break;
    }
    // save final image in temp
    NSString *imagePath = [self saveImage:newImage];
    //resolve with image path
    resolve(@{@"path":imagePath, @"width":[NSNumber numberWithFloat:newImage.size.width], @"height":[NSNumber numberWithFloat:newImage.size.height]});
}

// Merge images (default, Setting: mergeType.MERGE)
- (UIImage *)merge:(NSArray *)imagePaths {
    NSMutableArray *images = [@[] mutableCopy];
    CGSize contextSize = CGSizeMake(0, 0);
    for (id tempObject in imagePaths) {
        NSURL *URL = [RCTConvert NSURL:tempObject];
        NSData *imgData = [[NSData alloc] initWithContentsOfURL:URL];
        if (imgData != nil)
        {
            UIImage *image = [[UIImage alloc] initWithData:imgData];
            [images addObject:image];
            
            CGFloat width = image.size.width;
            CGFloat height = image.size.height;
            if (width > contextSize.width || height > contextSize.height) {
                contextSize = CGSizeMake(width, height);
            }
        }
    }
    
    // create context with size
    UIGraphicsBeginImageContext(contextSize);
    
    // loop through image array
    for (UIImage *image in images) {
        [image drawInRect:CGRectMake(0,0,contextSize.width,contextSize.height)];
    }
    
    // creating final image
    UIImage *mergedImage = UIGraphicsGetImageFromCurrentImageContext();
    UIGraphicsEndImageContext();
    return mergedImage;
}

// Create collage from images (Setting: mergeType.COLLAGE)
- (UIImage *)collage:(NSArray *)imagePaths {
    @try {
        NSMutableArray *imagesData = [[NSMutableArray alloc]init];
        NSMutableArray *maxRowHeights = [[NSMutableArray alloc]init];
        NSMutableArray *maxColumnWidths = [[NSMutableArray alloc]init];
        NSInteger columnWitdhSum = 0, rowHeightSum = 0, tempMaxRowHeight = 0;

        // Get image metaData, ignore images without metadata
        for (id tempObject in imagePaths) {
            NSURL *URL = [RCTConvert NSURL:tempObject];
            NSData *imgData = [[NSData alloc] initWithContentsOfURL:URL];
            if (imgData != nil) {
                UIImage *image = [[UIImage alloc] initWithData:imgData];
                [imagesData addObject:image];
            }
        }
        
        // Calc grid dimensions
        NSInteger staticColumns = 2; // TODO: Works with all kinds of columns, could be added as feature to set column parameter
        NSInteger imagesDataSize = [imagesData count];
        NSInteger columns = imagesDataSize >= staticColumns ? staticColumns : imagesDataSize;
        NSInteger rows = (NSInteger)ceilf((float) imagesDataSize / (float)columns);
        
        // Calculate contentSize dimensions
        for (int i = 0; i < (int)[imagesData count]; i++) {
            UIImage *imageData = [imagesData objectAtIndex: i];
            @try {
                if ([[maxColumnWidths objectAtIndex: (i % columns)] integerValue] < imageData.size.width) {
                    [maxColumnWidths replaceObjectAtIndex:(i % columns) withObject: @(imageData.size.width)];
                }
            }
            @catch (NSException *exception) {
                // If no value is set
                [maxColumnWidths insertObject: @(imageData.size.width) atIndex: i % columns];
            }
        
            if (tempMaxRowHeight < imageData.size.height) {
                tempMaxRowHeight = imageData.size.height;
            }
            if ((i + 1) % columns == 0 || i + 1 == [imagesData count]) {
                rowHeightSum += tempMaxRowHeight;
                [maxRowHeights addObject: @(tempMaxRowHeight)];
                tempMaxRowHeight = 0;
            }
        }
    
        // Sum up maxColumnWidths
        for (NSNumber *maxColumnWidth in maxColumnWidths) {
            columnWitdhSum += [maxColumnWidth integerValue];
        }
        
        // Create context with size
        NSInteger spacing = 20;
        CGFloat width = columnWitdhSum + ((columns + 1) * spacing);
        CGFloat height = rowHeightSum + ((rows + 1) * spacing);
        CGSize contextSize = CGSizeMake(width, height);
        UIGraphicsBeginImageContext(contextSize);
        // Set background color
        [[UIColor blackColor] set];
        UIRectFill(CGRectMake(0.0, 0.0, width, height));
        
        // Merge images
        NSInteger left = spacing, top = spacing, centerPaddingXSum = 0;
        
        for (int i = 0; i < (int)[imagesData count]; i++) {
            UIImage *imageData = [imagesData objectAtIndex: i];
            // position correctly
            if (i > 0 && columns > 0 && i % columns == 0) {
                left = spacing;
                centerPaddingXSum = 0;
                top += spacing + [[maxRowHeights objectAtIndex:((i - 1) / columns)] integerValue];
            }
            
            NSInteger centerPaddingX = ([[maxColumnWidths objectAtIndex:(i % columns)] integerValue] - imageData.size.width) / 2;
            NSInteger centerPaddingY = ([[maxRowHeights objectAtIndex:(i / columns)] integerValue] - imageData.size.height) / 2;
            NSInteger canvasLeft = left + centerPaddingXSum + centerPaddingX;
            NSInteger canvasTop = top + centerPaddingY;
            NSInteger canvasWidth =  imageData.size.width;
            NSInteger canvasHeight = imageData.size.height;

            [imageData drawInRect:CGRectMake(canvasLeft, canvasTop, canvasWidth, canvasHeight)];
            centerPaddingXSum += 2 * centerPaddingX;
            left += spacing + imageData.size.width;
        }
        
        // creating final image
        UIImage *collageImage = UIGraphicsGetImageFromCurrentImageContext();
        UIGraphicsEndImageContext();
        return collageImage;
    }
    @catch (NSException *exception) {
        NSLog(@"%@", exception.reason);
    }
    return nil;
}
@end

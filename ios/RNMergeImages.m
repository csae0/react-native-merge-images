
#import "RNMergeImages.h"
#import <React/RCTConvert.h>

@implementation RNMergeImages

NSInteger DEFAULT_MERGE_TYPE = 0;
NSInteger DEFAULT_JPEG_QUALITY = 80;
NSInteger DEFAULT_MAX_COLUMNS = 2;
NSInteger DEFAULT_IMAGE_SPACING = 20;
NSInteger DEFAULT_MAX_SIZE_IN_MB = 10;
NSString *DEFAULT_BACKGROUND_COLOR_HEX = @"#FFFFFF";

- (NSString *)saveImage:(UIImage *)image withMaxSizeInMB:(NSInteger) maxSizeInMB withFilename:(NSString *) filenamePrefix {
    NSString *filename = [filenamePrefix length] == 0 ? [[NSProcessInfo processInfo] globallyUniqueString] : filenamePrefix;
    NSString *fullPath = [NSString stringWithFormat:@"%@%@.jpg", NSTemporaryDirectory(), filename];
    // Compress image to specified size
    NSData *imageData = [self compressTo:maxSizeInMB image:image];
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
                    @"LARGEST": @(1),
                    @"SMALLEST": @(0),
                },
             @"target": @{
                 @"TEMP": @(1),
                 @"DISK": @(0),
                 },
             @"mergeType": @{
                     @"MERGE": @(0),
                     @"COLLAGE": @(1),
                     }
             };
}

RCT_EXPORT_METHOD(merge:(NSArray *)imagePaths
                  options:(NSDictionary *)options
                  resolver:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject)
{
    @try {
        NSInteger mergeType = [options valueForKey:@"mergeType"] != nil ? [RCTConvert NSInteger:options[@"mergeType"]] : DEFAULT_MERGE_TYPE;
        NSInteger maxColumns = [options valueForKey:@"maxColumns"] != nil ? [RCTConvert NSInteger:options[@"maxColumns"]] : DEFAULT_MAX_COLUMNS;
        NSInteger imageSpacing = [options valueForKey:@"imageSpacing"] != nil ? [RCTConvert NSInteger:options[@"imageSpacing"]] : DEFAULT_IMAGE_SPACING;
        NSInteger maxSizeInMB = [options valueForKey:@"maxSizeInMB"] != nil ? [RCTConvert NSInteger:options[@"maxSizeInMB"]] : DEFAULT_MAX_SIZE_IN_MB;
        NSString *filenamePrefix = [options valueForKey:@"filenamePrefix"] != nil ? [RCTConvert NSString:options[@"filenamePrefix"]] : @"";
        NSString *backgroundColor = [options valueForKey:@"backgroundColorHex"] != nil ? [RCTConvert NSString:options[@"backgroundColorHex"]] : DEFAULT_BACKGROUND_COLOR_HEX;
        
        UIImage *newImage = nil;

        switch (mergeType) {
            case 0:
                newImage = [self merge: imagePaths];
                break;
            case 1:
                newImage = [self collage: imagePaths withSpacing: imageSpacing withBackgroundColor: backgroundColor withMaxColumns: maxColumns];
                break;
            default:
                newImage = [self merge: imagePaths];
                break;
        }
        // save final image in temp
        NSString *imagePath = [self saveImage:newImage withMaxSizeInMB:maxSizeInMB withFilename:filenamePrefix];
        //resolve with image path
        resolve(@{@"path":imagePath, @"width":[NSNumber numberWithFloat:newImage.size.width], @"height":[NSNumber numberWithFloat:newImage.size.height]});
    } @catch(NSException *exception) {
        NSError *error = [NSError errorWithDomain:@"com.mergeImages" code:0 userInfo:@{@"Error reason": exception.reason}];
        reject(exception.reason, exception.description, error);
    }
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
- (UIImage *)collage:(NSArray *)imagePaths withSpacing:(NSInteger) imageSpacing withBackgroundColor:(NSString *) backgroundColor withMaxColumns:(NSInteger) maxColumns {
    @autoreleasepool {
        @try {
            NSMutableArray *imagesMetadata = [[NSMutableArray alloc]init];
            NSMutableArray *maxRowHeights = [[NSMutableArray alloc]init];
            NSMutableArray *maxColumnWidths = [[NSMutableArray alloc]init];
            NSInteger columnWitdhSum = 0, rowHeightSum = 0, tempMaxRowHeight = 0;

            // Get image metaData, ignore images without metadata
            for (id tempObject in imagePaths) {
                NSURL *URL = [RCTConvert NSURL:tempObject];
                NSData *imgData = [[NSData alloc] initWithContentsOfURL:URL];
                if (imgData != nil) {
                    UIImage *image = [[UIImage alloc] initWithData:imgData];
                    [imagesMetadata addObject:@{ @"width": @(image.size.width), @"height": @(image.size.height), @"url": URL}];
                }
            }
            
            // Calc grid dimensions
            NSInteger imagesDataSize = [imagesMetadata count];
            NSInteger columns = imagesDataSize >= maxColumns ? maxColumns : imagesDataSize;
            NSInteger rows = (NSInteger)ceilf((float) imagesDataSize / (float)columns);
            
            // Calculate contentSize dimensions
            for (int i = 0; i < (int)[imagesMetadata count]; i++) {
                NSDictionary *imageMetadata = [imagesMetadata objectAtIndex: i];
                @try {
                    if ([[maxColumnWidths objectAtIndex: (i % columns)] integerValue] < [imageMetadata[@"width"] floatValue]) {
                        [maxColumnWidths replaceObjectAtIndex:(i % columns) withObject: imageMetadata[@"width"]];
                    }
                }
                @catch (NSException *exception) {
                    // If no value is set
                    [maxColumnWidths insertObject: imageMetadata[@"width"] atIndex: i % columns];
                }
            
                if (tempMaxRowHeight < [imageMetadata[@"height"] floatValue]) {
                    tempMaxRowHeight = [imageMetadata[@"height"] floatValue];
                }
                if ((i + 1) % columns == 0 || i + 1 == [imagesMetadata count]) {
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
            CGFloat width = columnWitdhSum + ((columns + 1) * imageSpacing);
            CGFloat height = rowHeightSum + ((rows + 1) * imageSpacing);
            
            // Check if enough memory available to create large dimension bitmap
            bool fitsInMemory = [self hasEnoughMemory:@{@"width": @(width), @"height": @(height), @"bytesPerPixel": @(4)}];
            if (!fitsInMemory) {
                NSException *e = [NSException
                                  exceptionWithName:@"OutOfMemoryException"
                                  reason:@"oom"
                                  userInfo:nil];
                @throw e;
            }
            
            CGSize contextSize = CGSizeMake(width, height);
            UIGraphicsBeginImageContextWithOptions(contextSize, YES, 0.0);
            
            // Set background color
            [[self colorFromHexCode: backgroundColor] set];
            UIRectFill(CGRectMake(0.0, 0.0, width, height));
            // Merge images
            NSInteger left = imageSpacing, top = imageSpacing, centerPaddingXSum = 0;
            
            for (int i = 0; i < (int)[imagesMetadata count]; i++) {
                NSDictionary * imageMetadata = [imagesMetadata objectAtIndex: i];
                NSData *imageData = [[NSData alloc] initWithContentsOfURL:imageMetadata[@"url"]];
                UIImage *image = [[UIImage alloc] initWithData:imageData];
                // position correctly
                if (i > 0 && columns > 0 && i % columns == 0) {
                    left = imageSpacing;
                    centerPaddingXSum = 0;
                    top += imageSpacing + [[maxRowHeights objectAtIndex:((i - 1) / columns)] integerValue];
                }
                
                NSInteger centerPaddingX = ([[maxColumnWidths objectAtIndex:(i % columns)] integerValue] - [imageMetadata[@"width"] floatValue]) / 2;
                NSInteger centerPaddingY = ([[maxRowHeights objectAtIndex:(i / columns)] integerValue] - [imageMetadata[@"height"] floatValue]) / 2;
                NSInteger canvasLeft = left + centerPaddingXSum + centerPaddingX;
                NSInteger canvasTop = top + centerPaddingY;
                NSInteger canvasWidth =  [imageMetadata[@"width"] floatValue];
                NSInteger canvasHeight = [imageMetadata[@"height"] floatValue];

                [image drawInRect:CGRectMake(canvasLeft, canvasTop, canvasWidth, canvasHeight)];
                centerPaddingXSum += 2 * centerPaddingX;
                left += imageSpacing + [imageMetadata[@"width"] floatValue];
            }
            // creating final image
            UIImage *collageImage = UIGraphicsGetImageFromCurrentImageContext();
            UIGraphicsEndImageContext();
            
            return collageImage;
        } @finally {
            UIGraphicsEndImageContext();
        }
    }
}

- (NSData *)compressTo:(NSInteger)sizeInMb image:(UIImage *)soucreImage {
    NSInteger sizeInBytes = sizeInMb * 1024 * 1024;
    bool needCompress = true;
    CGFloat compressingValue = 1.0;

    while (needCompress && compressingValue > 0.0) {
        NSData *imageData = UIImageJPEGRepresentation(soucreImage, compressingValue);
        if (imageData) {
            if ([imageData length] <= sizeInBytes) {
                needCompress = false;
                return imageData;
            } else {
                compressingValue -= 0.1;
            }
        }
    }
    return nil;
}

- (UIColor *) colorFromHexCode:(NSString *)hexString {
    NSString *cleanString = [hexString stringByReplacingOccurrencesOfString:@"#" withString:@""];
    if([cleanString length] == 3) {
        cleanString = [NSString stringWithFormat:@"%@%@%@%@%@%@",
                       [cleanString substringWithRange:NSMakeRange(0, 1)],[cleanString substringWithRange:NSMakeRange(0, 1)],
                       [cleanString substringWithRange:NSMakeRange(1, 1)],[cleanString substringWithRange:NSMakeRange(1, 1)],
                       [cleanString substringWithRange:NSMakeRange(2, 1)],[cleanString substringWithRange:NSMakeRange(2, 1)]];
    }
    if([cleanString length] == 6) {
        cleanString = [cleanString stringByAppendingString:@"ff"];
    }
    
    unsigned int baseValue;
    [[NSScanner scannerWithString:cleanString] scanHexInt:&baseValue];
    
    float red = ((baseValue >> 24) & 0xFF)/255.0f;
    float green = ((baseValue >> 16) & 0xFF)/255.0f;
    float blue = ((baseValue >> 8) & 0xFF)/255.0f;
    float alpha = ((baseValue >> 0) & 0xFF)/255.0f;
    
    return [UIColor colorWithRed:red green:green blue:blue alpha:alpha];
}

- (long) getBitmapSizeInBytes: (NSDictionary *) data {
    NSInteger scale = (NSInteger)[UIScreen mainScreen].scale;
    long bytes = (long)[data[@"width"] integerValue] * (long)[data[@"height"] integerValue] * (long)[data[@"bytesPerPixel"] integerValue] * (long)scale;
    NSLog(@"TEST123 %i", ((int)bytes / 1024 / 1034));

    return bytes;
}

- (long) getAvaliableMemory {
    //TODO: IMPLEMENT
    return LONG_MAX;
}

- (Boolean) hasEnoughMemory: (NSDictionary *) data {
    NSInteger imageMemorySize = [self getBitmapSizeInBytes:data];
    NSInteger availableSystemMemory = [self getAvaliableMemory];
    return availableSystemMemory > imageMemorySize;
}

@end
//
//  CTUser.h
//  CheckpointTerminal
//
//  Created by Evgeny Kubrakov on 09/04/16.
//  Copyright © 2016 Streetmage. All rights reserved.
//

#import <Foundation/Foundation.h>

@interface CTUser : NSObject

@property (nonatomic, copy) NSString *name;
@property (nonatomic, copy) NSString *code;
@property (nonatomic, copy) NSString *phone;
@property (nonatomic, copy) NSNumber *kills;
@property (nonatomic, copy) NSNumber *deaths;

@property (nonatomic, copy) NSNumber *secondsCaptured;

+ (CTUser *)userWithJSON:(NSDictionary *)json;

@end

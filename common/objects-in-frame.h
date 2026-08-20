// License: Apache 2.0. See LICENSE file in root directory.
// Copyright(c) 2023 RealSense, Inc. All Rights Reserved.

#pragma once

#include "rect.h"
#include <string>
#include <vector>
#include <mutex>
#include <imgui.h>

enum class object_type
{
    person = 0,
    face = 1,
    other = 2
};

std::string object_type_to_string( object_type type );

struct object_in_frame
{
    rs2::rect normalized_color_bbox, normalized_depth_bbox;
    std::string name;
    float mean_depth;
    float metadata_depth;         // distance reported by the detection model (meters); 0 if unavailable
    float world_x, world_y;        // COM X, Y in camera coordinates (meters); valid only when com_valid (debug display)
    bool  com_valid = false;       // true when world_x/world_y (and mean_depth's source) came from firmware COM
    int   score;                  // detection confidence, 0-100
    size_t id;
    object_type type = object_type::other;

    object_in_frame( size_t _id, std::string const & _name, rs2::rect _bbox_color, rs2::rect _bbox_depth, float _depth,
                     float _metadata_depth, int _score,
                     object_type _type = object_type::other, float _world_x = 0.f, float _world_y = 0.f,
                     bool _com_valid = false )
        : normalized_color_bbox( _bbox_color )
        , normalized_depth_bbox( _bbox_depth )
        , name( _name )
        , mean_depth( _depth )
        , metadata_depth( _metadata_depth )
        , world_x( _world_x )
        , world_y( _world_y )
        , com_valid( _com_valid )
        , score( _score )
        , id( _id )
        , type( _type )
    {
    }
};


typedef std::vector< object_in_frame > objects_in_frame;


struct atomic_objects_in_frame : public objects_in_frame
{
    std::mutex mutex;
    bool sensor_is_on = true;
    int ticks_without_od_frame = 0;  // render ticks since last OD frame; cleared on every OD frame arrival
};

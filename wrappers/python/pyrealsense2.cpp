/* License: Apache 2.0. See LICENSE file in root directory.
Copyright(c) 2017 RealSense, Inc. All Rights Reserved. */

#include "pyrealsense2.h"
#include <librealsense2/rs.hpp>
#include <librealsense2/hpp/rs_export.hpp>
#include <src/types.h>
#include <condition_variable>
#include <deque>
#include <mutex>
#include <thread>

PYBIND11_MODULE(NAME, m) {
    m.doc() = R"pbdoc(
        Librealsense Python Bindings
        ==============================
        Library for accessing RealSense cameras
    )pbdoc";
    m.attr("__version__") = RS2_API_VERSION_STR;
    m.attr("__full_version__") = RS2_API_FULL_VERSION_STR;

    init_c_files(m);
    init_types(m);
    init_frame(m);
    init_options(m);
    init_processing(m);
    init_sensor(m);
    init_device(m);
    init_record_playback(m);
    init_context(m);
    init_pipeline(m);
    init_internal(m); // must be run after init_frame()
    init_export(m);
    init_advanced_mode(m);
    init_serializable_device(m);
    init_util(m);
    init_eth_config(m);
    
    /** rs_export.hpp **/
    py::class_<rs2::save_to_ply, rs2::filter>(m, "save_to_ply")
        .def(py::init<std::string, rs2::pointcloud>(), "filename"_a = "RealSense Pointcloud ", "pc"_a = rs2::pointcloud())
        .def_property_readonly_static("option_ignore_color", [](py::object) { return rs2::save_to_ply::OPTION_IGNORE_COLOR; })
        .def_property_readonly_static("option_ply_mesh", [](py::object) { return rs2::save_to_ply::OPTION_PLY_MESH; })
        .def_property_readonly_static("option_ply_binary", [](py::object) { return rs2::save_to_ply::OPTION_PLY_BINARY; })
        .def_property_readonly_static("option_ply_normals", [](py::object) { return rs2::save_to_ply::OPTION_PLY_NORMALS; })
        .def_property_readonly_static("option_ply_threshold", [](py::object) { return rs2::save_to_ply::OPTION_PLY_THRESHOLD; });

    m.def("log_to_console", &rs2::log_to_console, "min_severity"_a);
    m.def("log_to_file", &rs2::log_to_file, "min_severity"_a, "file_path"_a);
    m.def("reset_logger", &rs2::reset_logger);
    m.def("enable_rolling_log_file", &rs2::enable_rolling_log_file, "max_size"_a);

    // Access to log_message is only from a callback (see log_to_callback below) and so already
    // should have the GIL acquired
    py::class_<rs2::log_message> log_message(m, "log_message");
    log_message.def("line_number", &rs2::log_message::line_number)
        .def("filename", &rs2::log_message::filename)
        .def("raw", &rs2::log_message::raw)
        .def("full", &rs2::log_message::full)
        .def("__str__", &rs2::log_message::raw)
        .def("__repr__", &rs2::log_message::full);

    // We want to enable Python callbacks for logging, but need to be careful:
    // The machanism used by librealsense keeps a pointer to an object that is then released
    // on destruction/exit. Usually this works fine, except that here, with Python and its GIL,
    // Pybind tries to acquire the GIL when the thread state is no longer valid and we get
    // into an infinite wait.
    // This is how the code would look like if we didn't had this issue
#if 0
    m.def( "log_to_callback",
           []( rs2_log_severity min_severity, std::function< void( rs2_log_severity, rs2::log_message ) > callback )
           {
               py::gil_scoped_release gil;
               rs2::log_to_callback( min_severity,
                                     [callback]( rs2_log_severity severity, rs2::log_message const & msg ) noexcept
                                     {
                                         py::gil_scoped_acquire gil;
                                         callback( severity, msg );
                                     } );
           } );
#else
    // Instead, as a workaround, we override the usual mechanism to intentionally not free up
    // if we see the Python thread state isn't valid (see release() below):
    class py_log_callback : public rs2::log_callback
    {
        typedef rs2::log_callback super;

    public:
        py_log_callback( log_fn && on_log )
            : super( std::move( on_log ) )
        {
        }

        void on_log( rs2_log_severity severity, rs2_log_message const & msg ) noexcept override
        {
            try
            {
                // We're not being called from Python but instead are calling it,
                // we need to acquire it to not have issues with other threads...
                py::gil_scoped_acquire gil;
                super::on_log( severity, msg );
            }
            catch( std::exception const & e )
            {
                std::cerr << "EXCEPTION in " SNAME ".log_to_callback: " << e.what() << std::endl;
            }
            catch( ... )
            {
                std::cerr << "UNKNOWN EXCEPTION in " SNAME ".log_to_callback" << std::endl;
            }
        }

        void release() override
        {
            // When we exit() python, we get here with an invalid thread-state and the delete
            // locks the thread indefinitely!
            if( PyGILState_GetThisThreadState() )
                super::release();
        }
    };
    // The synchronous callback above acquires the GIL on whatever librealsense thread
    // emitted the log line. If that thread holds an internal mutex while the Python main
    // thread holds the GIL and blocks on that same mutex, the process deadlocks (AB-BA)
    // and even SIGTERM cannot kill it. The asynchronous path below breaks the cycle:
    // on_log only copies the message into a bounded queue (never touching the GIL), and a
    // dedicated worker thread -- which never holds librealsense locks -- acquires the GIL
    // and dispatches to Python. The cost: callbacks fire slightly after the log call, so
    // it is opt-in (asynchronous=True) and used by test infrastructure (--rslog).
    struct queued_log_message
    {
        unsigned _line;
        std::string _filename, _raw, _full;
    };
    py::class_< queued_log_message >( m, "queued_log_message",
                                      "Detached copy of a log_message, dispatched asynchronously" )
        .def( "line_number", []( queued_log_message const & self ) { return self._line; } )
        .def( "filename", []( queued_log_message const & self ) { return self._filename; } )
        .def( "raw", []( queued_log_message const & self ) { return self._raw; } )
        .def( "full", []( queued_log_message const & self ) { return self._full; } )
        .def( "__str__", []( queued_log_message const & self ) { return self._raw; } )
        .def( "__repr__", []( queued_log_message const & self ) { return self._full; } );

    class py_async_log_callback : public rs2_log_callback
    {
        typedef std::function< void( rs2_log_severity, queued_log_message const & ) > log_fn;

        log_fn _fn;
        std::mutex _mx;
        std::condition_variable _cv;
        std::deque< std::pair< rs2_log_severity, queued_log_message > > _queue;
        bool _stop = false;
        std::thread _worker;

        // Bound so a Python-side stall cannot grow memory without limit; excess is dropped
        enum : size_t { MAX_PENDING = 4096 };

    public:
        explicit py_async_log_callback( log_fn && fn )
            : _fn( std::move( fn ) )
            , _worker( [this] { run(); } )
        {
        }

        void on_log( rs2_log_severity severity, rs2_log_message const & msg ) noexcept override
        {
            // May run on any librealsense thread, possibly under internal locks: must not
            // block and must not touch the GIL. rs2::log_message's ctor is private, so
            // copy the fields out through the C API (ignoring per-field errors).
            try
            {
                auto get_str = []( const char * s ) { return std::string( s ? s : "" ); };
                rs2_error * e = nullptr;
                unsigned line = rs2_get_log_message_line_number( &msg, &e );
                if( e ) { rs2_free_error( e ); e = nullptr; line = 0; }
                std::string filename = get_str( rs2_get_log_message_filename( &msg, &e ) );
                if( e ) { rs2_free_error( e ); e = nullptr; }
                std::string raw = get_str( rs2_get_raw_log_message( &msg, &e ) );
                if( e ) { rs2_free_error( e ); e = nullptr; }
                std::string full = get_str( rs2_get_full_log_message( &msg, &e ) );
                if( e ) { rs2_free_error( e ); e = nullptr; }
                queued_log_message copy{ line,
                                         std::move( filename ),
                                         std::move( raw ),
                                         std::move( full ) };
                {
                    std::lock_guard< std::mutex > lock( _mx );
                    if( _queue.size() >= MAX_PENDING )
                        return;
                    _queue.emplace_back( severity, std::move( copy ) );
                }
                _cv.notify_one();
            }
            catch( ... )
            {
            }
        }

        // Called from a Python atexit handler, while the interpreter is still alive, so the
        // worker never tries to acquire the GIL after finalization. Caller must NOT hold the
        // GIL (the worker may need it to finish its current dispatch).
        void stop()
        {
            {
                std::lock_guard< std::mutex > lock( _mx );
                if( _stop )
                    return;
                _stop = true;
            }
            _cv.notify_one();
            if( _worker.joinable() )
                _worker.join();
        }

        void release() override
        {
            // Like py_log_callback: librealsense releases us at static destruction, when the
            // Python thread state may already be gone -- intentionally leak instead (the
            // worker was already stopped via atexit)
        }

    private:
        void run()
        {
            while( true )
            {
                std::pair< rs2_log_severity, queued_log_message > item;
                {
                    std::unique_lock< std::mutex > lock( _mx );
                    _cv.wait( lock, [this] { return _stop || ! _queue.empty(); } );
                    if( _queue.empty() )
                        return;  // only when stopping: flush whatever was queued first
                    item = std::move( _queue.front() );
                    _queue.pop_front();
                }
                try
                {
                    py::gil_scoped_acquire gil;
                    _fn( item.first, item.second );
                }
                catch( std::exception const & e )
                {
                    std::cerr << "EXCEPTION in " SNAME ".log_to_callback (async): " << e.what() << std::endl;
                }
                catch( ... )
                {
                    std::cerr << "UNKNOWN EXCEPTION in " SNAME ".log_to_callback (async)" << std::endl;
                }
            }
        }
    };

    m.def( "log_to_callback",
           []( rs2_log_severity min_severity, py::function callback, bool asynchronous )
           {
               rs2_error * e = nullptr;
               if( asynchronous )
               {
                   auto cb = new py_async_log_callback(
                       [callback]( rs2_log_severity severity, queued_log_message const & msg )
                       { callback( severity, msg ); } );
                   // Stop the worker while the interpreter is still alive; release the GIL
                   // around the join so the worker can finish an in-flight dispatch
                   py::module_::import( "atexit" ).attr( "register" )( py::cpp_function(
                       [cb]()
                       {
                           py::gil_scoped_release gil;
                           cb->stop();
                       } ) );
                   py::gil_scoped_release gil;
                   rs2_log_to_callback_cpp( min_severity, cb, &e );
               }
               else
               {
                   py_log_callback::log_fn fn
                       = [callback]( rs2_log_severity severity, rs2::log_message const & msg )
                       { callback( severity, msg ); };
                   py::gil_scoped_release gil;
                   rs2_log_to_callback_cpp( min_severity, new py_log_callback( std::move( fn ) ), &e );
               }
               rs2::error::handle( e );
           },
           "min_severity"_a, "callback"_a, "asynchronous"_a = false );
#endif

    // A call to rs.log() will cause a callback to get called! We should already own the GIL, but
    // release it just in case to let others do their thing...
    m.def("log", &rs2::log, "severity"_a, "message"_a, py::call_guard<py::gil_scoped_release>());
}

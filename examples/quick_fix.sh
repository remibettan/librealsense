#!/bin/bash
# Quick one-line fix for NumPy compatibility
pip install "numpy<2" --force-reinstall && pip install matplotlib --force-reinstall

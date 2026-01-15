# JupyterLab with PySpark 3.5.0 for Big Data Analytics
FROM jupyter/scipy-notebook:latest

USER root

# Install system dependencies
RUN apt-get update && apt-get install -y \
    openjdk-11-jdk \
    curl \
    wget \
    git \
    && rm -rf /var/lib/apt/lists/*

# Set JAVA_HOME
ENV JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64
ENV PATH=$PATH:$JAVA_HOME/bin

# Install PySpark 3.5.0
RUN pip install --no-cache-dir \
    pyspark==3.5.0 \
    SPARQLWrapper==1.8.5 \
    rdflib==7.0.0 \
    numpy==1.24.3 \
    pandas==2.0.3 \
    requests==2.31.0 \
    scikit-learn==1.3.2 \
    matplotlib==3.7.2 \
    seaborn==0.12.2 \
    pymysql==1.1.0 \
    sqlalchemy==2.0.23 \
    jupyterlab-git \
    jupyterlab-widgets \
    ipywidgets

# Install JupyterLab extensions
RUN jupyter labextension install @jupyter-widgets/jupyterlab-manager --no-build && \
    jupyter lab build --minimize=False

# Create workspace directories
RUN mkdir -p /home/jovyan/work/scripts \
    /home/jovyan/work/data \
    /home/jovyan/work/rdf-data \
    /home/jovyan/work/notebooks \
    /home/jovyan/work/models

# Set permissions
RUN chown -R jovyan:users /home/jovyan/work

USER jovyan

# Set working directory
WORKDIR /home/jovyan/work

# Configure JupyterLab
RUN jupyter lab --generate-config && \
    echo "c.ServerApp.token = ''" >> /home/jovyan/.jupyter/jupyter_lab_config.py && \
    echo "c.ServerApp.password = ''" >> /home/jovyan/.jupyter/jupyter_lab_config.py && \
    echo "c.ServerApp.ip = '0.0.0.0'" >> /home/jovyan/.jupyter/jupyter_lab_config.py && \
    echo "c.ServerApp.open_browser = False" >> /home/jovyan/.jupyter/jupyter_lab_config.py && \
    echo "c.ServerApp.allow_root = False" >> /home/jovyan/.jupyter/jupyter_lab_config.py

# Expose JupyterLab port
EXPOSE 8888

# Start JupyterLab
CMD ["jupyter", "lab", "--ip=0.0.0.0", "--port=8888", "--no-browser", "--allow-root", "--NotebookApp.token=''", "--NotebookApp.password=''"]


package com.tdclighthouse.prototype.services;

import java.io.File;
import java.io.FileFilter;

import javax.jcr.RepositoryException;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import com.tdclighthouse.hippo.beanmapper.annotations.NodeType;
import com.tdclighthouse.prototype.beanmapper.DynamicNodeWriter;
import com.tdclighthouse.prototype.jaxb.XmlDocument;
import com.tdclighthouse.prototype.services.FolderCreationService.NewFolderCallBackFactory;
import com.tdclighthouse.prototype.services.callbacks.NewFolderCallBack;
import com.tdclighthouse.prototype.support.AbstractSessionTemplate.SessionCallBack;
import com.tdclighthouse.prototype.support.DocumentManager;
import com.tdclighthouse.prototype.utils.ImportException;
import com.tdclighthouse.prototype.utils.PluginConstants;

public class XmlDocumentImportService extends AbstractImportService {

    private static final Logger LOG = LoggerFactory.getLogger(XmlDocumentImportService.class);

    private static final String DOCUMET_IMPORT_LOG_MESSAGE = "Docuemt: {} is going to be improt at {} under the name {}";
    @Autowired
    private DocumentManager documentManager;

    @Autowired
    private FolderCreationService folderCreationService;

    @Autowired
    private JAXBContext jaxbContext;

    @Value("${import.folder}")
    private String importFolder;

    @Autowired
    private DynamicNodeWriter dynamicNodeWriter;

    private ReferenceRegistry referenceRegistry;

    public XmlDocumentImportService() {
        filesFilter = new FileFilter() {

            @Override
            public boolean accept(File file) {
                return file.isFile() && file.getName().endsWith(".xml");
            }
        };
    }

    @Override
    public void importItem(File file, String relPath) throws ImportException {
        try {
            Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
            Object object = unmarshaller.unmarshal(file);
            Class<? extends Object> clazz = object.getClass();
            if (object instanceof XmlDocument && clazz.isAnnotationPresent(NodeType.class)) {
                XmlDocument doc = (XmlDocument) object;
                String parentPath = folderCreationService.generateFolders(PluginConstants.Paths.DOCUMENTS,
                        relPath, new NewFolderCallBackFactory() {

                            @Override
                            public SessionCallBack<String> getNewFolderCallBack(String parentPath, String folderName) {
                                return new NewFolderCallBack(parentPath, folderName);
                            }
                        });
                LOG.info(DOCUMET_IMPORT_LOG_MESSAGE, file.getAbsolutePath(), parentPath, doc.getTitle());
                String uuid = dynamicNodeWriter.createOrUpdateNode(doc, parentPath, doc.getTitle());
                if (referenceRegistry != null && uuid != null) {
                    referenceRegistry.register(file.toURI().toString(), uuid);
                }
            } else {
                LOG.error("the file at {} could not be imported at {}, because the binding class {} has not been"
                        + " annotated by com.tdclighthouse.hippo.beanmapper.annotations.NodeType or is "
                        + "not an implentation of com.tdclighthouse.prototype.jaxb.XmlDocument",
                        file.getAbsolutePath(), relPath, clazz.getName());
            }

        } catch (JAXBException | RepositoryException e) {
            throw new ImportException(e);
        }
    }

    public void setDocumentManager(DocumentManager documentManager) {
        this.documentManager = documentManager;
    }

    public void setFolderCreationService(FolderCreationService folderCreationService) {
        this.folderCreationService = folderCreationService;
    }

    public void setJaxbContext(JAXBContext jaxbContext) {
        this.jaxbContext = jaxbContext;
    }

    public void setDynamicNodeWriter(DynamicNodeWriter dynamicNodeWriter) {
        this.dynamicNodeWriter = dynamicNodeWriter;
    }

    public void setImportFolder(String importFolder) {
        this.importFolder = importFolder;
    }

    public void setReferenceRegistry(ReferenceRegistry referenceRegistry) {
        this.referenceRegistry = referenceRegistry;
    }
}

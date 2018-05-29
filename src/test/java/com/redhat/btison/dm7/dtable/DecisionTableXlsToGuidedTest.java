package com.redhat.btison.dm7.dtable;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import org.drools.core.io.impl.ClassPathResource;
import org.drools.decisiontable.InputType;
import org.drools.decisiontable.SpreadsheetCompiler;
import org.drools.workbench.models.guided.dtable.backend.GuidedDTXMLPersistence;
import org.drools.workbench.models.guided.dtable.shared.conversion.ConversionResult;
import org.drools.workbench.models.guided.dtable.shared.model.GuidedDecisionTable52;
import org.drools.workbench.screens.drltext.type.DRLResourceTypeDefinition;
import org.drools.workbench.screens.dtablexls.backend.server.conversion.DecisionTableXLSToDecisionTableGuidedConverter;
import org.drools.workbench.screens.dtablexls.type.DecisionTableXLSResourceTypeDefinition;
import org.drools.workbench.screens.dtablexls.type.DecisionTableXLSXResourceTypeDefinition;
import org.drools.workbench.screens.globals.type.GlobalResourceTypeDefinition;
import org.drools.workbench.screens.guided.dtable.service.GuidedDecisionTableEditorService;
import org.drools.workbench.screens.guided.dtable.type.GuidedDTableResourceTypeDefinition;
import org.guvnor.common.services.shared.metadata.MetadataService;
import org.junit.BeforeClass;
import org.junit.Test;
import org.kie.api.KieBase;
import org.kie.api.KieServices;
import org.kie.api.builder.KieBuilder;
import org.kie.api.builder.KieFileSystem;
import org.kie.api.builder.Message;
import org.kie.api.definition.KiePackage;
import org.kie.api.io.Resource;
import org.kie.api.runtime.KieContainer;
import org.kie.soup.project.datamodel.commons.util.RawMVELEvaluator;
import org.kie.soup.project.datamodel.oracle.PackageDataModelOracle;
import org.kie.workbench.common.services.datamodel.backend.server.builder.packages.PackageDataModelOracleBuilder;
import org.kie.workbench.common.services.datamodel.backend.server.service.DataModelService;
import org.kie.workbench.common.services.shared.preferences.ApplicationPreferences;
import org.kie.workbench.common.services.shared.project.KieProject;
import org.kie.workbench.common.services.shared.project.KieProjectService;
import org.kie.workbench.common.services.shared.project.ProjectImportsService;
import org.mockito.Mockito;
import org.uberfire.backend.vfs.Path;
import org.uberfire.backend.vfs.PathFactory;
import org.uberfire.io.IOService;

public class DecisionTableXlsToGuidedTest {

    String resourceName = "interest-rate-calculation.xls";

    String outputDir = "/tmp";

    private PackageDataModelOracle dataModel;

    private DataModelService dataModelService;

    private IOService ioService;

    private Path path;

    private KieBase kBase;

    private KieProjectService projectService;

    private KieProject project;

    private Path expectedProjectImportsPath;

    private ProjectImportsService importService;

    private MetadataService metadataService;

    private GuidedDecisionTableEditorService guidedDecisionTableEditorService;

    @BeforeClass
    public static void setup() {
        setupPreferences();
    }

    private static void setupPreferences() {
        final Map<String, String> preferences = new HashMap<String, String>() {{
            put(ApplicationPreferences.DATE_FORMAT,
                    "dd/mm/yyyy");
        }};
        ApplicationPreferences.setUp(preferences);
    }

    @Test
    public void testConvertXlsToGuidedDtable() throws Exception {

        KieServices kieServices = KieServices.Factory.get();
        KieFileSystem kfs = kieServices.newKieFileSystem();

        SpreadsheetCompiler compiler = new SpreadsheetCompiler();
        Resource resource = new ClassPathResource("interest-rate-calculation.xls");
        String drl = compiler.compile(resource.getInputStream(), InputType.XLS);
        kfs.write("src/main/resources/interest-rate-calculation.drl", drl);
        KieBuilder kb = kieServices.newKieBuilder(kfs);
        kb.buildAll();
        if (kb.getResults().hasMessages(Message.Level.ERROR)) {
            throw new RuntimeException("Build errors\n" + kb.getResults().toString());
        }

        KieContainer kieContainer = kieServices.newKieContainer(kieServices.getRepository().getDefaultReleaseId());
        kBase = kieContainer.getKieBase();

        URL url = Thread.currentThread().getContextClassLoader().getResource(resourceName);
        path = PathFactory.newPath(resourceName, url.toURI().toString());

        dataModel = mock(PackageDataModelOracle.class);
        when(dataModel.getPackageName()).thenReturn(getRulesPackage(kBase));
        when(dataModel.getProjectModelFields()).thenReturn(new HashMap<>());

        ioService = mock(IOService.class);
        when(ioService.newInputStream(any(org.uberfire.java.nio.file.Path.class))).thenReturn(new URL(path.toURI()).openStream());

        dataModelService = mock(DataModelService.class);
        when(dataModelService.getDataModel(Mockito.eq(path))).thenReturn(dataModel);

        project = mock(KieProject.class);
        projectService = mock(KieProjectService.class);
        expectedProjectImportsPath = mock(Path.class);
        when(projectService.resolveProject(any(Path.class))).thenReturn(project);
        when(project.getImportsPath()).thenReturn(expectedProjectImportsPath);
        when(expectedProjectImportsPath.toURI()).thenReturn("default://project0/project.imports");

        importService = mock(ProjectImportsService.class);
        metadataService = mock(MetadataService.class);

        guidedDecisionTableEditorService = (GuidedDecisionTableEditorService) Proxy.newProxyInstance(Thread.currentThread().getContextClassLoader(),
                new Class[] {GuidedDecisionTableEditorService.class}, new GuidedDecisionTableEditorServiceInvocationHandler());

        DecisionTableXLSResourceTypeDefinition xlsDTableType = new DecisionTableXLSResourceTypeDefinition();
        DecisionTableXLSXResourceTypeDefinition xlsxDTableType = new DecisionTableXLSXResourceTypeDefinition();
        GuidedDTableResourceTypeDefinition guidedDTableType = new GuidedDTableResourceTypeDefinition();
        DRLResourceTypeDefinition drlType = new DRLResourceTypeDefinition();
        GlobalResourceTypeDefinition globalsType = new GlobalResourceTypeDefinition();
        DecisionTableXLSToDecisionTableGuidedConverter converter = new DecisionTableXLSToDecisionTableGuidedConverter(ioService, null,
                guidedDecisionTableEditorService, null, projectService, importService, metadataService, null, dataModelService, null, xlsDTableType, xlsxDTableType, guidedDTableType, drlType, globalsType);
        ConversionResult result = converter.convert(path);

    }

    private String getRulesPackage(KieBase kbase) {
        for (KiePackage p : kbase.getKiePackages()) {
            if (p.getRules().size() > 0) {
                return p.getName();
            }
        }
        return "org.test";
    }

    private PackageDataModelOracle buildDataModel() {
        PackageDataModelOracleBuilder dmoBuilder = PackageDataModelOracleBuilder.newPackageOracleBuilder(new RawMVELEvaluator(), getRulesPackage(kBase));
        return dmoBuilder.build();
    }

    public class GuidedDecisionTableEditorServiceInvocationHandler implements InvocationHandler {

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (method.getName().equals("create")) {
                File output = new File(outputDir, (String) args[1]);
                BufferedWriter writer = new BufferedWriter(new FileWriter(output));
                writer.write(GuidedDTXMLPersistence.getInstance().marshal((GuidedDecisionTable52) args[2]));
                writer.close();

            }
            return null;
        }
    }

}

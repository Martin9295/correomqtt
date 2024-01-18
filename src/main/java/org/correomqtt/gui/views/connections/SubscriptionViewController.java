package org.correomqtt.gui.views.connections;

import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.AnchorPane;
import org.correomqtt.business.connection.DisconnectEvent;
import org.correomqtt.business.eventbus.EventBus;
import org.correomqtt.business.eventbus.Subscribe;
import org.correomqtt.business.fileprovider.PersistSubscribeHistoryUpdateEvent;
import org.correomqtt.business.model.ConnectionConfigDTO;
import org.correomqtt.business.model.ControllerType;
import org.correomqtt.business.model.MessageDTO;
import org.correomqtt.business.model.MessageListViewConfig;
import org.correomqtt.business.model.Qos;
import org.correomqtt.business.model.SubscriptionDTO;
import org.correomqtt.business.fileprovider.PersistSubscriptionHistoryProvider;
import org.correomqtt.business.fileprovider.SettingsProvider;
import org.correomqtt.business.pubsub.IncomingMessageEvent;
import org.correomqtt.business.pubsub.SubscribeEvent;
import org.correomqtt.business.pubsub.SubscribeTask;
import org.correomqtt.business.pubsub.UnsubscribeEvent;
import org.correomqtt.business.pubsub.UnsubscribeTask;
import org.correomqtt.business.utils.ConnectionHolder;
import org.correomqtt.gui.views.cell.QosCell;
import org.correomqtt.gui.contextmenu.SubscriptionListMessageContextMenu;
import org.correomqtt.gui.contextmenu.SubscriptionListMessageContextMenuDelegate;
import org.correomqtt.gui.utils.AlertHelper;
import org.correomqtt.gui.utils.CheckTopicHelper;
import org.correomqtt.gui.model.MessagePropertiesDTO;
import org.correomqtt.gui.model.SubscriptionPropertiesDTO;
import org.correomqtt.gui.transformer.MessageTransformer;
import org.correomqtt.gui.transformer.SubscriptionTransformer;
import org.correomqtt.gui.views.LoaderResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class SubscriptionViewController extends BaseMessageBasedViewController implements
        SubscriptionListMessageContextMenuDelegate {

    private static final Logger LOGGER = LoggerFactory.getLogger(SubscriptionViewController.class);
    private static ResourceBundle resources;
    private final SubscriptionViewDelegate delegate;

    @FXML
    public AnchorPane subscribeBodyViewAnchor;

    @FXML
    public ComboBox<Qos> qosComboBox;

    @FXML
    public ComboBox<String> subscribeTopicComboBox;

    @FXML
    public ListView<SubscriptionPropertiesDTO> subscriptionListView;

    @FXML
    private Button unsubscribeButton;

    @FXML
    private Button unsubscribeAllButton;

    @FXML
    private Button selectAllButton;

    @FXML
    private Button selectNoneButton;
    private boolean afterSubscribe;

    public SubscriptionViewController(String connectionId, SubscriptionViewDelegate delegate) {
        super(connectionId);
        this.delegate = delegate;
        EventBus.register(this);
    }

    static LoaderResult<SubscriptionViewController> load(String connectionId, SubscriptionViewDelegate delegate) {
        LoaderResult<SubscriptionViewController> result = load(SubscriptionViewController.class, "subscriptionView.fxml",
                () -> new SubscriptionViewController(connectionId, delegate));
        resources = result.getResourceBundle();
        return result;
    }

    @FXML
    public void initialize() {

        initMessageListView();

        qosComboBox.setItems(FXCollections.observableArrayList(Qos.values()));
        qosComboBox.getSelectionModel().selectFirst();
        qosComboBox.setCellFactory(QosCell::new);


        subscriptionListView.setItems(FXCollections.observableArrayList(SubscriptionPropertiesDTO.extractor()));
        subscriptionListView.setCellFactory(this::createCell);

        unsubscribeButton.setDisable(true);
        unsubscribeAllButton.setDisable(true);
        selectAllButton.setDisable(true);
        selectNoneButton.setDisable(true);

        subscribeTopicComboBox.getEditor().lengthProperty().addListener((observable, oldValue, newValue) -> {
            CheckTopicHelper.checkSubscribeTopic(subscribeTopicComboBox, false, afterSubscribe);
            if (!newValue.toString().isEmpty()) {
                afterSubscribe = false;
            }
        });

        SettingsProvider.getInstance().getConnectionConfigs().stream()
                .filter(c -> c.getId().equals(getConnectionId()))
                .findFirst()
                .ifPresent(c -> {
                    if (!splitPane.getDividers().isEmpty()) {
                        splitPane.getDividers().get(0).setPosition(c.getConnectionUISettings().getSubscribeDividerPosition());
                    }
                    super.messageListViewController.showDetailViewButton.setSelected(c.getConnectionUISettings().isSubscribeDetailActive());
                    super.messageListViewController.controllerType = ControllerType.SUBSCRIBE;
                    if (c.getConnectionUISettings().isSubscribeDetailActive()) {
                        super.messageListViewController.showDetailView();
                        if (!super.messageListViewController.splitPane.getDividers().isEmpty()) {
                            super.messageListViewController.splitPane.getDividers().get(0).setPosition(c.getConnectionUISettings().getSubscribeDetailDividerPosition());
                        }
                    }
                });

        initTopicComboBox();

    }

    private void initTopicComboBox() {
        List<String> topics = PersistSubscriptionHistoryProvider.getInstance(getConnectionId()).getTopics(getConnectionId());
        subscribeTopicComboBox.setItems(FXCollections.observableArrayList(topics));
        subscribeTopicComboBox.setCellFactory(TopicCell::new);

    }

    private ListCell<SubscriptionPropertiesDTO> createCell(ListView<SubscriptionPropertiesDTO> listView) {
        SubscriptionViewCell cell = new SubscriptionViewCell(listView);
        SubscriptionListMessageContextMenu contextMenu = new SubscriptionListMessageContextMenu(this);
        cell.setContextMenu(contextMenu);
        cell.itemProperty().addListener((observable, oldValue, newValue) -> contextMenu.setObject(cell.getItem()));
        cell.selectedProperty().addListener((observable, oldValue, newValue) -> {
            if (Boolean.TRUE.equals(newValue)) {
                onSubscriptionSelected(cell.getItem());
            }
        });
        return cell;
    }

    private void subscribe() {
        if (!CheckTopicHelper.checkSubscribeTopic(subscribeTopicComboBox, true, afterSubscribe)) {
            return;
        }

        String topic = subscribeTopicComboBox.getValue();

        if (topic == null || topic.isEmpty()) {
            LOGGER.info("Topic must not be empty");
            subscribeTopicComboBox.getStyleClass().add("emptyError");
            return;
        }

        Qos selectedQos = qosComboBox.getSelectionModel().getSelectedItem();
        new SubscribeTask(getConnectionId(), SubscriptionDTO.builder()
                .topic(topic)
                .qos(selectedQos)
                .build())
                .onError(this::onSubscribedFailed)
                .run();

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Subscribing to topic '{}': {}", topic, getConnectionId());
        }
    }

    private SubscriptionPropertiesDTO getSelectedSubscription() {
        return subscriptionListView.getSelectionModel().getSelectedItem();
    }

    @FXML
    private void onClickUnsubscribe() {

        SubscriptionPropertiesDTO selectedSubscription = getSelectedSubscription();

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Unsubscribe from topic '{}' clicked: {}", selectedSubscription.getTopic(), getConnectionId());
        }
        if (selectedSubscription != null) {
            unsubscribe(selectedSubscription);
        }
    }

    public void unsubscribe(SubscriptionPropertiesDTO subscriptionDTO) {
        new UnsubscribeTask(getConnectionId(), SubscriptionTransformer.propsToDTO(subscriptionDTO)).run();
    }

    @FXML
    public void selectAll() {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Select all topics for filter clicked: {}", getConnectionId());
        }
        subscriptionListView.getItems().forEach(subscriptionDTO -> subscriptionDTO.setFiltered(true));
    }

    @Override
    public void filterOnly(SubscriptionPropertiesDTO dto) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Filter only topic '{}': {}", dto.getTopic(), getConnectionId());
        }
        subscriptionListView.getItems().forEach(item -> item.setFiltered(dto.equals(item)));
    }

    @FXML
    public void selectNone() {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Select none topic for filter clicked: {}", getConnectionId());
        }
        subscriptionListView.getItems().forEach(subscriptionDTO -> subscriptionDTO.setFiltered(false));
    }

    @FXML
    private void onClickUnsubscribeAll() {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Unsubscribe from all topics clicked: {}", getConnectionId());
        }
        unsubscribeAll();
    }

    public void unsubscribeAll() {
        ConnectionHolder.getInstance()
                .getConnection(getConnectionId())
                .getClient()
                .getSubscriptions()
                .forEach(s -> new UnsubscribeTask(getConnectionId(), s));

        subscriptionListView.getItems().clear();

        unsubscribeButton.setDisable(true);
        unsubscribeAllButton.setDisable(true);
        selectAllButton.setDisable(true);
        selectNoneButton.setDisable(true);
    }

    /**
     * @param actionEvent The event given by JavaFX.
     */
    public void onClickSubscribe(@SuppressWarnings("unused") ActionEvent actionEvent) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Subscribe to topic clicked: {}", getConnectionId());
        }
        subscribe();
    }

    public void onClickSubscribeKey(KeyEvent actionEvent) {
        if (actionEvent.getCode() == KeyCode.ENTER) {
            subscribeTopicComboBox.setValue(subscribeTopicComboBox.getEditor().getText());
            if (subscribeTopicComboBox.getValue() == null) {
                return;
            }
            subscribe();
        }
    }

    @FXML
    public void onSubscriptionSelected(SubscriptionPropertiesDTO subscriptionDTO) {
        unsubscribeButton.setDisable(subscriptionDTO == null);
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Subscription selected '{}': {}", subscriptionDTO == null ? "N/A" : subscriptionDTO.getTopic(), getConnectionId());
        }
    }

    // TODO: if all existing subscriptions are not filtered and a new comes in, no new messages are shown in the list
    // only after reclick the checkbox it works

    @SuppressWarnings("unused")
    @Subscribe
    public void onMessageIncoming(IncomingMessageEvent event) {
        MessagePropertiesDTO messagePropertiesDTO = MessageTransformer.dtoToProps(event.getMessageDTO());
        messagePropertiesDTO.getSubscriptionDTOProperty().setValue(SubscriptionTransformer.dtoToProps(event.getSubscriptionDTO()));
        messageListViewController.onNewMessage(messagePropertiesDTO);
    }

    @SuppressWarnings("unused")
    @Subscribe
    public void onSubscribedSucceeded(SubscribeEvent event) {
        afterSubscribe = true;
        subscribeTopicComboBox.getSelectionModel().select("");

        if (event.getSubscriptionDTO().isHidden()) {
            return;
        }

        SubscriptionPropertiesDTO subscriptionPropertiesDTO = SubscriptionTransformer.dtoToProps(event.getSubscriptionDTO());
        subscriptionListView.getItems().add(0, subscriptionPropertiesDTO);
        unsubscribeAllButton.setDisable(false);
        selectAllButton.setDisable(false); //TODO disable on demand
        selectNoneButton.setDisable(false); //TODO disable on demand

        subscriptionPropertiesDTO.getFilteredProperty().addListener((observable, oldValue, newValue) -> {
            if (!Objects.equals(oldValue, newValue)) {
                updateFilter();
            }
        });
    }

    public void onSubscribedFailed(Throwable exception) {
        String msg = "Exception in business layer: " + exception.getMessage();
        AlertHelper.warn(resources.getString("subscribeViewControllerSubscriptionFailedTitle") + ": ", msg);
    }

    private void updateFilter() {

        Set<String> filteredTopics = subscriptionListView.getItems()
                .stream()
                .filter(dto -> dto.getFilteredProperty().getValue())
                .map(dto -> dto.getTopicProperty().getValue())
                .collect(Collectors.toSet());

        messageListViewController.setFilterPredicate(m -> {
            SubscriptionPropertiesDTO subscription = m.getSubscription();
            if (subscription == null) {
                return false;
            }
            return filteredTopics.contains(subscription.getTopic());

        });

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Filter updated {}", getConnectionId());
        }
    }

    @Override
    public void setUpToForm(MessagePropertiesDTO messageDTO) {
        delegate.setUpToForm(messageDTO);
    }

    @Override
    public Supplier<MessageListViewConfig> produceListViewConfig() {
        return () -> SettingsProvider.getInstance()
                .getConnectionConfigs()
                .stream()
                .filter(c -> c.getId().equals(getConnectionId()))
                .findFirst()
                .orElse(ConnectionConfigDTO.builder().subscribeListViewConfig(new MessageListViewConfig()).build())
                .produceSubscribeListViewConfig();

    }


    @SuppressWarnings("unused")
    @Subscribe(DisconnectEvent.class)
    public void onDisconnect() {
        subscriptionListView.getItems().clear();
    }

    @SuppressWarnings("unused")
    @Subscribe(PersistSubscribeHistoryUpdateEvent.class)
    public void updateSubscriptions() {
        initTopicComboBox();
    }


    @SuppressWarnings("unused")
    public void onUnsubscribeSucceeded(@Subscribe UnsubscribeEvent event) {

        SubscriptionPropertiesDTO subscriptionToRemove = subscriptionListView.getItems().stream()
                .filter(s -> s.getTopic().equals(event.getSubscriptionDTO().getTopic()))
                .findFirst()
                .orElse(null);

        if (subscriptionToRemove != null) {
            subscriptionListView.getItems().remove(subscriptionToRemove);
            unsubscribeButton.setDisable(true);

            if (subscriptionListView.getItems().isEmpty()) {
                unsubscribeAllButton.setDisable(true);
                selectAllButton.setDisable(true);
                selectNoneButton.setDisable(true);
            }
        }
    }

    @Override
    public void removeMessage(MessageDTO messageDTO) {
        // nothing to do
    }

    @Override
    public void clearMessages() {
        // nothing to do
    }

    @Override
    public void setTabDirty() {
        delegate.setTabDirty();
    }

    public void cleanUp() {
        this.messageListViewController.cleanUp();
        EventBus.unregister(this);
    }
}
